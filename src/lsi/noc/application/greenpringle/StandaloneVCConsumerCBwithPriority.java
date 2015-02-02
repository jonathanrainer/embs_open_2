package lsi.noc.application.greenpringle;

import java.util.Iterator;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.Time;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import lsi.noc.application.PlatformCommunicationInterface;


public class StandaloneVCConsumerCBwithPriority extends PlatformCommunicationInterface{

	public StandaloneVCConsumerCBwithPriority(CompositeEntity container,
			String name) throws NameDuplicationException,
			IllegalActionException {
		super(container, name);
		
		
		//port instantiations
		packet_out   = new TypedIOPort(this, "packet_out", false, true);
		ack_out   = new TypedIOPort(this, "ack_out", false, true);
        data_in  = new TypedIOPort(this, "data_in", true, false);
        
        ack_out.setMultiport(true);
        ack_out.setTypeEquals(BaseType.INT);
        data_in.setMultiport(true);
        
		period = new Parameter(this, "Period");
		period.setTypeEquals(BaseType.DOUBLE);
		period.setExpression("period");
        
	}
	
	
	
    public void initialize() throws IllegalActionException {
        super.initialize();

        
        //initialize state control variables
        vcs=data_in.getWidth();
        state_ = new int[vcs];
        flitCounter_ = new int[vcs];
        
        for(int i=0;i<vcs;i++){
        	state_[i]=RECEIVING_HEADER;
        	flitCounter_[i]=0;
            
        }
        
        period_ = ((DoubleToken)period.getToken()).doubleValue();
        
    }
	
	
    
    public void fire() throws IllegalActionException {
       
    	
    	
    	for(int i=0;i<vcs;i++){
            if (data_in.hasToken(i)) {
    	
    	
            	if (state_[i] == RECEIVING_HEADER) {
         
            		RecordToken tmp = (RecordToken)data_in.get(i);
            		if(_debugging) _debug("vc: "+i+" - address received: " + tmp + " TIME: " + getDirector().getModelTime());
            		

            		
            		// Send an ack
            		sendAck(i);
            		state_[i] = RECEIVING_SIZE;
            	}
        	

        		else if (state_[i] == RECEIVING_SIZE) {
                    
            		RecordToken tmp = (RecordToken)data_in.get(i);
            		if(_debugging) _debug("vc: "+i+" - size received: " + tmp + " TIME: " + getDirector().getModelTime());
            		
            		flitCounter_[i] = ((IntToken)tmp.get("size")).intValue(); 

            		
            		// Send an ack
            		sendAck(i);
            		state_[i] = RECEIVING_PAYLOAD;
            	}
            	
            	else if (state_[i] == RECEIVING_PAYLOAD) {
             
            		RecordToken tmp = (RecordToken)data_in.get(i);
            		if(_debugging) _debug("vc: "+i+" - payload received: " + tmp + " TIME: " + getDirector().getModelTime());
                            
            		// Send an ack
            		sendAck(i);
             		flitCounter_[i]--;
             
             		if (flitCounter_[i]==0) { //full packet received
                 
             			currentTime_ = getDirector().getModelTime();
             			
        				String[] labels = new String[1];
        				Token[] values = new Token[1];
        				
        				labels[0] = "commfinishtime";
        				values[0] = new DoubleToken(this.getCurrentTime().getDoubleValue()+period_); // reception ends at the end of the clock cycle
        				RecordToken finishTime = new RecordToken(labels, values); 
        				
        				tmp = RecordToken.merge(finishTime, tmp); // add the communication finish time to the RecordToken
             			
        				packet_out.send(0, tmp); // sends out the packet received notification 
        				
             			state_[i] = RECEIVING_HEADER;
                 
             		}
            
            	}
            }
    	}
        
        
    }
    
    protected void sendAck(int i) throws IllegalActionException {
        IntToken t = new IntToken(); 
        ack_out.send(i, t);
    	
    }
    
	public Time getCurrentTime() {
		
		return this.getDirector().getModelTime();
	}

	
	public void pruneDependencies() {
		super.pruneDependencies();

       	removeDependency(ack_out, data_in);
       	removeDependency(data_in,ack_out);

       	removeDependency(packet_out, data_in);
	}
	
    public TypedIOPort ack_out; // credit replenishing bits
    public TypedIOPort data_in; // received flits
    public TypedIOPort packet_out; // packet received notification

    
    
    static final int RECEIVING_HEADER = 0;
    static final int RECEIVING_SIZE = 1;
    static final int RECEIVING_PAYLOAD = 2;
    protected int vcs;
    protected int[] state_;
    protected int[] flitCounter_;
    private Time currentTime_;
	public Parameter period;
	protected double period_;
    


	

}
