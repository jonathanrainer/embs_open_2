package lsi.noc.application.greenpringle;

import java.util.Vector;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.RecordType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import lsi.noc.application.PlatformCommunicationInterface;



/**
 * @version 1.0 (York, 09/12/2014)
 * @author Leandro Soares Indrusiak
 * 
 * Standalone producer for cycle-accurate model of a credit-based virtual-channel-enabled Network-on-Chip.
 * Receives incoming tasks through a trigger port. Tasks should be represented as RecordTokens with at least the following labels:
 * "comptime" (DOUBLE) - computation time of the task
 * "releasetime" (DOUBLE) - release time of the task
 * "compfinishtime" (DOUBLE) - time when task computation has finished (updated by this actor)
 * "x" (INT) - x coordinate of the destination of the packet sent by the task 
 * "y" (INT) - y coordinate of the destination of the packet sent by the task 
 * "size" (INT) - length of the packet's payload
 * "priority" (INT) - task's (and respective packet's) priority
 * 
 *
 */

@SuppressWarnings("serial")
public class StandaloneVCProducerCBwithPriorityNonpreemptiveScheduler extends PlatformCommunicationInterface {

	protected Time currentTime_;
	protected Time taskReadyTime_;
	TypedIOPort trigger, ack_in, data_out; // port declarations
	protected double period_;
	public Parameter cred, per;
	protected int[] credits_; //stores the credits available on remote buffer
	protected Vector[] taskBuffers_; //one queue per priority level to store scheduled tasks
	protected Vector[] buffers_; //one queue per priority level to store scheduled packets
	protected int[] flitCounter_; //amount of flits to be transferred, for each virtual channel 
	protected int vcs; // number of virtual channels
	protected boolean first_;
	protected int size_;
	protected boolean taskBusy_;
	protected boolean busy_;
	protected RecordToken runningTask_;
    protected RecordToken packetToSend_;
    protected String[] labels_;
//    protected Token[] values_;



	public StandaloneVCProducerCBwithPriorityNonpreemptiveScheduler(CompositeEntity container, String name)


	throws NameDuplicationException, IllegalActionException  {
		super(container, name);

		// port instantiations
		trigger = new TypedIOPort(this, "trigger", true, false); // receives tokens representing tasks triggered to execute
		ack_in   = new TypedIOPort(this, "ack_in", true, false); // receives one credit from the NoC router
		data_out = new TypedIOPort(this, "data_out", false, true);

		// enable credit_in and data_out to handle multiple channels, which will model virtual channels
		ack_in.setMultiport(true);
		data_out.setMultiport(true);

		// instantiate parameter to hold initial number of credits of each output port (equivalent to the buffer depth of the respective downstream FIFO buffer        
		cred=new Parameter(this, "Initial credits");
		cred.setTypeEquals(BaseType.INT);
		cred.setExpression("capacity");
		
		// instantiate parameter to hold clock period        
		per = new Parameter(this, "Period");
		per.setTypeEquals(BaseType.DOUBLE);
		per.setExpression("period");

		
		
		
		// Labels and types for a packet 
        labels_ = new String[10];
        Type[] types = new Type[10];
        
        labels_[0] = "x";
        labels_[1] = "y";
        labels_[2] = "size";
        labels_[3] = "priority";
        labels_[4] = "id";
        labels_[5] = "releasetime";
        labels_[6] = "period";
        labels_[7] = "comptime";
        labels_[8] = "compfinishtime";
        labels_[9] = "commfinishtime";

        types[0] = BaseType.INT;
        types[1] = BaseType.INT;
        types[2] = BaseType.INT;
        types[3] = BaseType.INT;
        types[4] = BaseType.INT;
        types[5] = BaseType.DOUBLE;
        types[6] = BaseType.DOUBLE;
        types[7] = BaseType.DOUBLE;
        types[8] = BaseType.DOUBLE;
        types[9] = BaseType.DOUBLE;
        
        
        
        RecordType declaredType = new RecordType(labels_, types);
        data_out.setTypeEquals(declaredType);
		
		
		

	}

	public void initialize() throws IllegalActionException {
		super.initialize();

		currentTime_= getDirector().getModelTime();
		busy_ = false;
		taskBusy_=false;
		first_ = true;
		packetToSend_ = null;
		runningTask_ = null;



		//all output ports and vcs have full credits upon initialisation

		vcs=data_out.getWidth(); // deduce the number of VCs by the width of the output channel
		credits_=new int[vcs];
		flitCounter_=new int[vcs];
		buffers_=new Vector[vcs];
		taskBuffers_=new Vector[vcs];
		int fullcredits=((IntToken)cred.getToken()).intValue();

		if(_debugging) _debug("initializing producer with "+vcs+" VCs, each with credits: "+fullcredits);            
		if(_debugging) _debug("building packet and task queues");            

		for(int i=0;i<vcs;i++) {
			credits_[i]=fullcredits;
			buffers_[i]=new Vector();
			taskBuffers_[i]=new Vector();
			flitCounter_[i]=0;
		}



	}

	public boolean prefire() throws IllegalActionException {

		if (_debugging) _debug("Pre fire: current time = " + getDirector().getModelTime().getDoubleValue());
		if (_debugging) _debug("Producer = " + this.toString());


		//update credits of each VC
		for(int i=0;i<vcs;i++){
			if (ack_in.hasToken(i)) {
				// reads the token, increment credit
				IntToken credit = (IntToken) ack_in.get(i);
				credits_[i]++;

				if(_debugging) _debug(getDirector().getModelTime()+" vc: "+i+" - credit update received: " + credits_);

			}
		}    	
		return true;
	}

	public void fire() throws IllegalActionException {
		
		// deals with new tasks
		
		while(trigger.hasToken(0)){
			
			Token newTask = trigger.get(0);
			if(newTask instanceof RecordToken){
				
				RecordToken task = (RecordToken)newTask;
				IntToken pri = (IntToken)task.get("priority");

				
				// adds new task to scheduler process list at the respective priority level
				addToTaskBuffer_(task, pri.intValue());
				
			}
			
			
		}
		

		if(taskBusy_){ // if CPU busy with tasks, check whether current task has terminated

			if(getCurrentTime().compareTo(taskReadyTime_)>=0){
				
				taskBusy_= false;
				
				
				// updates "compfinishtime" field of the task representation (or adds one if it hasn't)
				String[] labels = new String[1];
				Token[] values = new Token[1];
				
				labels[0] = "compfinishtime";
				values[0] = new DoubleToken(this.getCurrentTime().getDoubleValue());
				RecordToken finishTime = new RecordToken(labels, values);
				
				runningTask_ = RecordToken.merge(finishTime, runningTask_);
				
				// schedules packet transmission
				IntToken pri = (IntToken) runningTask_.get("priority");
				addToBuffer_(runningTask_, pri.intValue());
				
			}

		
		}
		
		if(!taskBusy_){ // if CPU idle, schedule the highest priority task from the queues

			for(int i=0;i<vcs;i++){ // checks the queues in priority order hi->lo
				if(!taskBuffers_[i].isEmpty()){ // if there are queued tasks at this priority
					
					runningTask_ = (RecordToken)taskBuffers_[i].remove(0);

					
					taskBusy_=true;
					
					double comptime = ((DoubleToken)runningTask_.get("comptime")).doubleValue();
					
			        taskReadyTime_ = getCurrentTime().add(comptime);
					getDirector().fireAt(this, taskReadyTime_);
					
					break; //exits the loop once one task is scheduled, so only the highest priority task is executed
				}
			}
		}
		
		
		// deals with flits
		for(int i=0;i<vcs;i++){

			if(_debugging) _debug("for VC "+i);
			if(_debugging) _debug("    packets to send "+buffers_[i].size());
			if(_debugging) _debug("    credits on vc: "+credits_[i]);
			if(_debugging) _debug("    compare time? "+currentTime_.compareTo(getDirector().getModelTime()));
			if(_debugging) _debug("    first? "+first_);

			if (buffers_[i].size() > 0 && credits_[i]>0 && ((-1==currentTime_.compareTo(getDirector().getModelTime()))||first_)) {
				first_= false;
				busy_ = true;
				packetToSend_ = (RecordToken)buffers_[i].firstElement();
				size_ = ((IntToken)packetToSend_.get("size")).intValue();

				if(_debugging) _debug(currentTime_+" sending one flit of packet of size " + size_ + " via VC "+i);            

				data_out.send(i, packetToSend_);
				credits_[i]--;
				currentTime_ = getDirector().getModelTime();
				getDirector().fireAt(this, currentTime_.add(period_));

				if(_debugging) _debug("remaining credits: " + credits_[i]);

				flitCounter_[i]++;
				if (flitCounter_[i] == (size_)+2) { // must include the header and the size flits
					busy_ = false;
					flitCounter_[i] = 0;
					buffers_[i].remove(0);
				}
				break; //exits the loop once one flit is sent, so only the highest priority flit is sent
			} 
		}   
	}




	protected void addToTaskBuffer_(RecordToken packet, int priority) {

		if(_debugging) _debug("a task was released with priority "+priority);

		taskBuffers_[priority].addElement(packet);
		
	}
	
	
	protected void addToBuffer_(RecordToken packet, int priority) {

		if(_debugging) _debug("a packed was scheduled with priority "+priority);

		buffers_[priority].addElement(packet);
	}


	public void pruneDependencies() {
		super.pruneDependencies();

		removeDependency(data_out, ack_in);
		removeDependency(ack_in, data_out);
	}

	public Time getCurrentTime() {
		
		return this.getDirector().getModelTime();
	}




}
