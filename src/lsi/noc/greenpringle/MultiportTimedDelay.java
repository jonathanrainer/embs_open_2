package lsi.noc.greenpringle;

import java.util.Vector;

import ptolemy.actor.util.Time;
import ptolemy.actor.util.TimedEvent;
import ptolemy.data.Token;


import ptolemy.domains.de.lib.TimedDelay;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;



//////////////////////////////////////////////////////////////////////////
////MultiportTimedDelay

/**
  * This actor extends TimedDelay by redefining its ports to be multiports
  * and by changing its firing scheme to handle tokens arriving to the multiple
  * channels of the input port. 
  * All tokens are delayed by the same amount of time, regardless of the channel
  * they arrive. They will be sent to the output via the same channel they 
  * arrived from.
  *
  *
  *

   @see ptolemy.domains.de.lib.TimedDelay

   @author Leandro Soares Indrusiak
  */

public class MultiportTimedDelay extends TimedDelay {

	public MultiportTimedDelay(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException {
		super(container, name);
		output.setMultiport(true);
		input.setMultiport(true);
		
	}

	
	public void initialize() throws IllegalActionException{
		
		super.initialize();
		vec=new Vector();
		
	}
	
	// overrides the firing scheme of the superclass
	
    public void fire() throws IllegalActionException {


        // consume the input of the channel with smallest index
        // if more than one channel has a token, they will be dealt with 
        // during the next firing(s)
        
        for(int i=0;i<input.getWidth();i++){
        
        	if (input.hasToken(i)) {

        		
        		// the input token is stored on _currentInput
        		// its channel is stored on a vector, using the token 
        		// itself as "key" (stored before it)
        		
        		
        		_currentInput= input.get(i);
        		vec.add(_currentInput);
        		vec.add(new Integer(i));
            	if(_debugging) _debug("vector size: "+vec.size());
                if(_debugging) _debug("read token on channel " + i);
        		break;
        		
        	} else {
        		_currentInput = null;
        	}
        }
        // produce output
        // NOTE: The amount of delay may be zero.
        // In this case, if there is already some token scheduled to
        // be produced at the current time before the current input
        // arrives, that token is produced. While the current input
        // is delayed to the next available firing at the current time.
        Time currentTime = getDirector().getModelTime();
        _currentOutput = null;

        if (_delayedOutputTokens.size() > 0) {
            TimedEvent earliestEvent = (TimedEvent) _delayedOutputTokens.get();
            Time eventTime = earliestEvent.timeStamp;

            if (eventTime.equals(currentTime)) {
            	_currentOutput=(Token)earliestEvent.contents;
            	int index=vec.indexOf(_currentOutput);
            	int i = ((Integer)vec.elementAt(index+1)).intValue();
            	output.send(i, _currentOutput);
            	if(_debugging) _debug("wrote token on channel " + i);
            	vec.remove(index+1);
            	vec.remove(index);
            	
            	if(_debugging) _debug("vector size: "+vec.size());
            	
            }
        }
    }
	
    ///////////////////////////////////////////////////////////////////
    ////                         protected variables               ////

    /** Stores the channel of each Token.
     */
    protected Vector vec;
}
