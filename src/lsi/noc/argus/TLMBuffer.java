package lsi.noc.argus;

import ptolemy.actor.TypedIOPort;
import ptolemy.actor.lib.Transformer;
import ptolemy.actor.util.FIFOQueue;
import ptolemy.actor.util.Time;
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


////TLMBuffer 

/**
This actor implements a FIFO Buffer.  When a token is received on the
<i>input</i> port, it is stored in the queue.
The oldest token on the FIFO is sent to the <i>output</i> port periodically
according to the <i>period</i> parameter.
When the <i>read</i> port receives a token, the oldest element in the
buffer is discarded.
<p>
The input can be of any token type, and the output
is constrained to be of a type at least that of the input. If
the <i>capacity</i> parameter is negative or zero (the default),
then the capacity is infinite. Otherwise, the capacity is
given by that parameter, and inputs received when the queue
is full are discarded. Whenever the size of the queue changes,
the new size is produced on the <i>size</i> output port.
If an input arrives at the same time that an output is
produced, then the <i>size</i> port gets two events at
the same time.

The implementation was inspired on the
ptolemy.domains.de.lib.Queue class by Lee and Neuendorffer. The major
difference is the fact that this buffer has an implicit trigger. <br><br>

Copyright (c) 2007 - All rights reserved. <br>

@author Leandro Soares Indrusiak
@version 1.0 (Darmstadt, 19.11.2007)

 */

public class TLMBuffer extends Transformer{


	public TLMBuffer(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		output.setTypeAtLeast(input);
		read = new TypedIOPort(this, "read", true, false);
		ack = new TypedIOPort(this, "ack", false, true);
		_queue = new FIFOQueue();
		
		//buffer capacity
		capacity = new Parameter(this, "capacity");
		capacity.setTypeEquals(BaseType.INT);
		capacity.setExpression("0");

		//period of a cycle
		period = new Parameter(this, "period");
		period.setTypeEquals(BaseType.DOUBLE);

	}


	///////////////////////////////////////////////////////////////////
	////                     ports and parameters                  ////

	/** The capacity of the buffer. If the value is positive, then
	 *  it specifies the capacity of the queue. If it is negative
	 *  or 0, then it specifies that the capacity is infinite.
	 *  This is an integer with default 0.
	 */
	public Parameter capacity;

	/** The read port, which has undeclared type. If this port
	 *  receives a token, then the oldest token in the queue
	 *  will be discarded.
	 */
	public TypedIOPort read;

	/** The ack port, which has undeclared type. If an input token is
	 *  added to the FIFO, a <i>true</i> BooleanToken is sent out through
	 *  this port. If the FIFO is full, the input token is discarded and
	 *  a <i>false</i> BooleanToken is sent out through this port.
	 */
	public TypedIOPort ack;


	//cycle period
	public Parameter period;

	/** Reacts to a change in an attribute.  If the attribute is
	 *  <i>capacity</i>, then change the capacity of the queue.
	 *  If the size of the queue currently exceeds the specified
	 *  capacity, then throw an exception.
	 *  @param attribute The attribute that changed.
	 *  @exception IllegalActionException If the current size
	 *   of the queue exceeds the specified capacity.
	 */
	public void attributeChanged(Attribute attribute)
	throws IllegalActionException {
		if (attribute == capacity) {
			int newCapacity = ((IntToken) capacity.getToken()).intValue();
			if (newCapacity <= 0) {
				if (_queue.getCapacity() != FIFOQueue.INFINITE_CAPACITY) {
					_queue.setCapacity(FIFOQueue.INFINITE_CAPACITY);
				}
			} else {
				if (newCapacity < _queue.size()) {
					throw new IllegalActionException(this, "Queue size ("
							+ _queue.size() + ") exceed requested capacity "
							+ newCapacity + ").");
				}
				_queue.setCapacity(newCapacity);
			}
		} else {
			super.attributeChanged(attribute);
		}
	}



	/** Clone the actor into the specified workspace. This calls the
	 *  base class and then sets the ports.
	 *  @param workspace The workspace for the new object.
	 *  @return A new actor.
	 *  @exception CloneNotSupportedException If a derived class has
	 *   has an attribute that cannot be cloned.
	 */
	public Object clone(Workspace workspace) throws CloneNotSupportedException {
		TLMBuffer newObject = (TLMBuffer) super.clone(workspace);
		newObject._queue = new FIFOQueue();
		newObject.output.setTypeAtLeast(newObject.input);
		return newObject;
	}


	/** If there is an input on the <i>read</i> port, remove the 
	 *  oldest token from the queue.
	 *  
	 *  Always returns true.  (Not sure why, though)
	 *  
	 *  @exception IllegalActionException If there is no director.
	 */
	public boolean prefire() throws IllegalActionException {

		boolean hasReadTrigger = false;

		if (read.getWidth() > 0) {
			hasReadTrigger = (read.hasToken(0));
		}

		//if read notification is received, remove the oldest token
		//from the queue
		if(hasReadTrigger){
			// Consume the trigger token.
			read.get(0);
			_queue.take();
		}

		return true;
	}




	/** If there is a new token
	 *  on the <i>input</i> port, then put it on the FIFO.
	 *
	 *  If the queue is not empty, then send a copy of
	 *  the oldest token on the queue to the <i>output</i> port.
	 *  
	 *  <NOT ANYMORE,SINCE MOELLER TOUCHED IT>
	 *  Send the resulting FIFO size to the <i>size</i> output port.
	 *  </NOT ANYMORE,SINCE MOELLER TOUCHED IT>
	 *  
	 *  @exception IllegalActionException If getting tokens from input and
	 *  read ports or sending token to output throws it.
	 */
	public void fire() throws IllegalActionException {
		super.fire();

		//sends copy of the oldest token to the output
		int comp = getDirector().getModelTime().compareTo(nextFlit);
		if (_queue.size() != 0 && (comp==0 | comp==1) ) {

		//this seems to be Moeller's stuff
		//not sure, though, as he didn't comment anything
		//I guess he is gathering together the flit and the buffer occupation
	    //to get his scopes to work.
			
			String[] label = new String[1];
		    Token[] value = new Token[1];
	        label[0]="buffer_size";
	        value[0]=new IntToken(_queue.size());
		    RecordToken recordToken1 = new RecordToken(label,value);
		    RecordToken recordToken2 = (RecordToken)_queue.get(0);
		    RecordToken recordToken3=RecordToken.merge(recordToken1,recordToken2);
			output.send(0, (Token)recordToken3);
			if(_debugging) _debug("data sent at: " + getDirector().getModelTime());
		}


		//reads input port
		//stores token on the FIFO if there is room
		//sends ack or nack regarding the storage of the token
		if (input.hasToken(0)) {
			Token k =input.get(0);
			if(_queue.size()<_queue.getCapacity()){
				_queue.put(k);
				ack.send(0, new BooleanToken(true));
			}
			else {
				ack.send(0, new BooleanToken(false));
			}
		}

		if (_queue.size() != 0){
			//requests new firing after period
			nextFlit=getDirector().getModelTime();
			DoubleToken per =(DoubleToken)period.getToken();
			nextFlit=nextFlit.add(per.doubleValue());
			getDirector().fireAt(this,nextFlit);
		}

	}

	/** Clear the cached input tokens.
	 *  @exception IllegalActionException If there is no director.
	 */
	public void initialize() throws IllegalActionException {
		_queue.clear();
		nextFlit=getDirector().getModelTime();
		super.initialize();
	}


	/** Override the base class to declare that the <i>output</i>
	 *  does not depend on the <i>input</i> in a firing.
	 */
	public void pruneDependencies() {
		super.pruneDependencies();
		removeDependency(input, output);
		removeDependency(read, output);
	}

	/** The FIFOQueue. */
	protected FIFOQueue _queue;
	protected Time nextFlit;
}
