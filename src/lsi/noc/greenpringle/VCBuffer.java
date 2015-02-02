/**
 * 
 */
package lsi.noc.greenpringle;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.util.FIFOQueue;
import ptolemy.data.BooleanToken;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;
import lsi.noc.argus.TLMBuffer;

/**
 * @author Leandro Soares Indrusiak 
 * @version 0.1 (York, 15/1/2009)
 * @version 0.2 (York, 28/1/2009)
 * @version 1.0 (York, 29/1/2009)
 */
public class VCBuffer extends TLMBuffer {

	public VCBuffer(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		output.setMultiport(true);
		input.setMultiport(true);
		read.setMultiport(true);
		ack.setMultiport(true);


	}


	//Sets the number of virtual channels based on the width of the input multiport.
	//Width of output, read and ack multiports must be the same.
	//Upon initialization, creates an array of FIFOs, one per virtual channel. 
	//FIFO of the superclass is used as the FIFO of the first channel, so it behaves
	//exactly as the superclass if no additional virtual channels are used.

	public void initialize() throws IllegalActionException {
		super.initialize();

		int i= input.getWidth();
		int o= output.getWidth();
		int r= read.getWidth();
		int a= ack.getWidth();

		if(o!=i)throw new IllegalActionException("incorrect virtual channel input/output ratio");
		if(a!=i)throw new IllegalActionException("incorrect virtual channel input/ack ratio");
		if(r!=o)throw new IllegalActionException("incorrect virtual channel output/read ratio");

		_queues = new FIFOQueue[i];
		_queues[0]=_queue;

		int cap = ((IntToken) capacity.getToken()).intValue();
		for(int j=1;j<i;j++){
			_queues[j]=new FIFOQueue();
			_queues[j].setCapacity(cap);
		}



	}


	// TODO
	// Currently it simply call the superclass methods.
	// Must be implemented, so that if the capacity parameter changes,
	// it should throw an exception if any of the FIFOs of the array
	// currently has more tokens than the new capacity.
	// The superclass method does that for _queues[0] only (because it 
	// is the original _queue of the regular one-dimensional buffer).

	public void attributeChanged(Attribute attribute)
	throws IllegalActionException {
		super.attributeChanged(attribute);
	}



	public Object clone(Workspace workspace) throws CloneNotSupportedException {

		VCBuffer newObject = (VCBuffer) super.clone(workspace);
		try {
			newObject.output.setMultiport(true);
			newObject.input.setMultiport(true);
			newObject.read.setMultiport(true);
			newObject.ack.setMultiport(true);		} catch (IllegalActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			newObject.output.setTypeAtLeast(newObject.input);

			return newObject;
	}




	/** If there is an input on the <i>read</i> multiport, remove the 
	 *  oldest token from the respective queue.
	 *  
	 *  Always returns true.  (Not sure why, though)
	 *  
	 *  @exception IllegalActionException If there is no director.
	 */
	public boolean prefire() throws IllegalActionException {


		for (int i=0;i<read.getWidth();i++){

			//if read notification is received, remove the oldest token
			//from the queue

			if(read.hasToken(i)){
				// Consume the trigger token.
				read.get(i);
				// Remove the oldest token
				_queues[i].take();
			}


		}

		return true;
	}



	/** If there are new tokens
	 *  on the <i>input</i> multiport, then put them in the 
	 *  respective FIFO.
	 *
	 *  For each queue, if it is not empty, send a copy of
	 *  the oldest token on the queue to the <i>output</i> multiport.
	 *  
	 *  
	 *  @exception IllegalActionException 
	 */
	public void fire() throws IllegalActionException {



		// This should happen at most once every period (determined by a parameter
		// of the superclass), so a timestamp comparison is made at every fire.
		// comp=0 means that current time is exactly the time to send the next copy
		// comp=1 means that current time after the time to send the next copy
		// comp=-1 means that current time is before the time to send the next copy




		int comp = getDirector().getModelTime().compareTo(nextFlit);
		if (comp==0 | comp==1) {

			// Sends copies of the oldest token of each queue to the 
			// respective channel of the output multiport.


			for (int i=0;i<_queues.length;i++){
				if(_queues[i].size() != 0){

					//this seems to be Moeller's stuff
					//not sure, though, as he didn't comment anything
					//I guess he is gathering together the flit and the buffer occupation
					//to get his scopes to work.


					String[] label = new String[1];
					Token[] value = new Token[1];
					label[0]="buffer_size";
					value[0]=new IntToken(_queues[i].size());
					RecordToken recordToken1 = new RecordToken(label,value);
					RecordToken recordToken2 = (RecordToken)_queues[i].get(0);
					RecordToken recordToken3=RecordToken.merge(recordToken1,recordToken2);
					output.send(i, (Token)recordToken3);
					if(_debugging) _debug("data sent at: " + getDirector().getModelTime());
				}
			}
		}



		// Reads each channel of the input multiport
		// Stores token on the respective FIFO if there is room
		// Sends ack or nack regarding the storage of the token
		// to the respective channel of the ack port.



		for (int i=0;i<input.getWidth();i++){	
			if (input.hasToken(i)) {
				Token k =input.get(i);
				if(_queues[i].size()<_queues[i].getCapacity()){
					_queues[i].put(k);
					ack.send(i, new BooleanToken(true));
				}
				else {
					ack.send(i, new BooleanToken(false));
					
				}
			}
		}


		// If there are tokens on any of the FIFOs, 
		// requests new firing after period.


		if (!_queuesEmpty()){

			nextFlit=getDirector().getModelTime();
			DoubleToken per =(DoubleToken)period.getToken();
			nextFlit=nextFlit.add(per.doubleValue());
			getDirector().fireAt(this,nextFlit);
			
		}


	}

	protected boolean _queuesEmpty(){

		for (int i=0;i<_queues.length;i++){
			if(_queues[i].size() != 0) return false;
		}

		return true;

	}

	protected FIFOQueue[] _queues;
}
