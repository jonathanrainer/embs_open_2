package lsi.noc.greenpringle;


import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class VCBufferCB extends VCBuffer {
	
	
	public VCBufferCB(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);
		
		ack.setTypeEquals(BaseType.INT);

	}
	
	
	
	/** If there is an input on the <i>read</i> multiport, remove the 
	 *  oldest token from the respective queue and notify remote output port
	 *  of the additional credit available.
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
				
				// notify available credit
				
				int cap = ((IntToken) capacity.getToken()).intValue();
				ack.send(i, new IntToken(cap-_queues[i].size()));
				
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
		// Stores token on the respective FIFO 



		for (int i=0;i<input.getWidth();i++){	
			if (input.hasToken(i)) {
				Token k =input.get(i);
				if(_queues[i].size()<_queues[i].getCapacity()){
					_queues[i].put(k);
				}
				else {
					
					//shouldn't happen
					//it means sender is not respecting the credit limits
					System.out.println("Error: attempt to send data without credit");
					System.out.println(this);
					System.out.println("input: "+i);
					System.out.println("FIFO occupation: "+_queues[i].size());
					
					
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
}
