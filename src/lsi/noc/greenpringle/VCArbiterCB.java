package lsi.noc.greenpringle;

import java.util.Iterator;
import java.util.List;



import ptolemy.data.BooleanToken;
import ptolemy.data.IntToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Nameable;

/**
 * A NoC router/arbiter that supports any arbitrary number of virtual channels
 * and arbitrate them on a priority based. Flow control is credit based.
 * 
 * @author Leandro Soares Indrusiak
 * @version 1.0 (York, 26/2/2009)
 */

@SuppressWarnings("serial")
public class VCArbiterCB extends VCArbiter {

	
    
    
	private int[][] credits; //stores the credits available on remote buffer
	
	public VCArbiterCB(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		

		
	}

	public void initialize() throws IllegalActionException {
		super.initialize();
		//all output ports and vcs have full credits upon initialization
		credits=new int[5][vcs];
		int max=getMaxCredits();
		for(int i=0; i<5;i++){
			for(int j=0;j<vcs;j++){
				credits[i][j]=max;
			}



			
		}
		
		
	}
	
	
	
	protected void processPayload() throws IllegalActionException{
		/***********************************
		 **********PROCESS PAYLOAD**********
		 ***********************************/
		//for each output multiport
		for(int i=0; i<5;i++){


			// send out to the remote buffer
			// the token of the highest priority virtual channel
			// that has credits
			// and discard all others

			boolean sent = false;


			for(int j=0;j<vcs;j++){


				if(muxout[i][j]!=-1){
					if(inputreq[muxout[i][j]].hasToken(j)&& state[muxout[i][j]][j]==SENDING){

						if(!sent && credits[i][j]!=0){
							
							
							int respectiveinput=muxout[i][j];
							
							//send token to output
							output[i].send(j, inputreq[respectiveinput].get(j));

							sent=true;
							if(_debugging) _debug(getDirector().getModelTime()+" sending flit from port "+respectiveinput+" vc "+ j +" via output "+i); 

							//decrement credits
							credits[i][j]--;

							//send notification back to buffer 
							read[respectiveinput].send(j,new Token());
							if(_debugging) _debug(getDirector().getModelTime()+" sending ack from port "+i+" "+j+" to input "+respectiveinput); 

							//decrement size packet size
							packetsize[respectiveinput][j]--;
							//if package is done, change state back to REQUESTING 
							//and reset the muxin/muxout tables
							if(packetsize[respectiveinput][j]==0){
								state[respectiveinput][j]=REQUESTING;
								muxin[respectiveinput][j]=-1;
								muxout[i][j]=-1;
								if(_debugging) _debug(getDirector().getModelTime()+" last flit of the package"); 

							}

							
						}
						else {
							inputreq[muxout[i][j]].get(j);
						}

					}

				}
			}
		}


	}

	protected void processAcks() throws IllegalActionException{



		/***********************************
		 **********PROCESS ACKS*************
		 ***********************************/
		//for each ack multiport
		for(int i=0; i<5;i++){

			// for each of ack's virtual channels
			// update the credit of their remote buffers
			for(int j=0;j<ack[i].getWidth();j++){
				if (ack[i].hasToken(j)){
					IntToken tok=(IntToken)ack[i].get(j);
//					credits[i][j]=tok.intValue();
					credits[i][j]++;
					}
				}
		}
	}

	
	
	
	protected int getMaxCredits(){

		//gets all attributes of the container of the arbiter
		//and those of the container's container
    	Nameable container = getContainer();
    	Nameable topcontainer = container.getContainer();
    	List l = ((CompositeEntity)container).attributeList();
    	List l1 =((CompositeEntity)topcontainer).attributeList();

    	//searchs for the buffer capacity parameter
    	Iterator it=l.iterator();
    	Iterator it1=l1.iterator();
        while(it.hasNext()){
        	Attribute a = (Attribute)it.next();
        	if(a instanceof Parameter){
        		
        		String name=((Parameter)a).getName();
        		if(name=="capacity"){
        			return Integer.parseInt(((Parameter)a).getExpression());
        		}
        	}
        	
        }
        
        while(it1.hasNext()){
        	Attribute a = (Attribute)it1.next();
        	if(a instanceof Parameter){
        		
        		String name=((Parameter)a).getName();
        		if(name=="capacity"){
        			return Integer.parseInt(((Parameter)a).getExpression());
        		}
        	}
        	
        }
        		  // FIXME        
        return 2; // error! couldn't find definition of buffer capacity 
        		  // on the two upper-level containers of the arbiter 
		
	}
	
	
	
	
}
