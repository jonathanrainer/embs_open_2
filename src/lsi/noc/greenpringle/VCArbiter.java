/**
 * 
 */
package lsi.noc.greenpringle;

import java.util.List;


import ptolemy.actor.util.Time;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedCompositeActor;
import ptolemy.actor.TypedIOPort;
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

/**
 * 
 * A NoC router/arbiter that supports any arbitrary number of virtual channels
 * and arbitrate them on a priority based. Flow control is based on handshake.
 * 
 * @author Leandro Soares Indrusiak
 * 
 * @version 1.0 (York, 6/2/2009)
 */
public class VCArbiter extends TypedAtomicActor {


	protected NamedObj container;


	public Parameter x, y; //position of the switch on the 2D mesh
	public Parameter routingdelay; //time to arbitrate and route a header
	public Parameter period;

	//arrays used to index ports
	protected TypedIOPort[] read, inputreq, output, ack;

	//port directions
	static final int EAST=0, WEST=1, NORTH=2, SOUTH=3, LOCAL=4;

	//port state-holding arrays, indexed by direction and virtual channel number
	protected Time[][] headertimestamp;
	protected RecordToken record;
	protected int[][] packetsize;  //amount of the flits yet to be transfer in the packet currently received by an input port
	protected int[][] state; //current state of a given input port
	protected int[][] muxin; //output port assigned to a given input port and virtual channel by the router
	protected int[][] muxout; //read port assigned to a given ack port and virtual channel by the router
	protected int vcs; //number of virtual channels

	//values for the port+channel state variables
	//	static final int INACTIVE=0; 
	static final int REQUESTING=0; //requesting arbiter
	static final int WAITING=1; //arbitration done, waiting delay
	static final int SENDING=2; //ready to send flits
	//	static final int DONE=4;  //closing connection





	public VCArbiter(CompositeEntity container, String name)
	throws NameDuplicationException, IllegalActionException {
		super(container, name);

		//instantiates the parameters that hold the grid position of this instance
		//on the 2D mesh
		x = new Parameter(this, "x");
		x.setTypeEquals(BaseType.INT);
		y = new Parameter(this, "y");
		y.setTypeEquals(BaseType.INT);


		//instantiates the parameter that define the number of cycles the
		//arbiter needs to select a port and route a header
		routingdelay = new Parameter(this, "arbitration cycles");
		routingdelay.setTypeEquals(BaseType.INT);

		//instantiates the parameter that define the period of a cycles
		period = new Parameter(this, "period");
		period.setTypeEquals(BaseType.DOUBLE);


		//instantiates ports and adds them to indexing arrays
		ack = new TypedIOPort[5];
		ack[0]= new TypedIOPort(this, "ackE", true, false);
		ack[1]= new TypedIOPort(this, "ackW", true, false);
		ack[2]= new TypedIOPort(this, "ackN", true, false);
		ack[3]= new TypedIOPort(this, "ackS", true, false);
		ack[4]= new TypedIOPort(this, "ackL", true, false);
		for(int i=0;i<5;i++)ack[i].setMultiport(true);


		inputreq = new TypedIOPort[5];
		inputreq[0]= new TypedIOPort(this, "inputreqE", true, false);
		inputreq[1]= new TypedIOPort(this, "inputreqW", true, false);
		inputreq[2]= new TypedIOPort(this, "inputreqN", true, false);
		inputreq[3]= new TypedIOPort(this, "inputreqS", true, false);
		inputreq[4]= new TypedIOPort(this, "inputreqL", true, false);
		for(int i=0;i<5;i++)inputreq[i].setMultiport(true);

		output = new TypedIOPort[5];
		output[0]= new TypedIOPort(this, "outputE", false, true);
		output[1]= new TypedIOPort(this, "outputW", false, true);
		output[2]= new TypedIOPort(this, "outputN", false, true);
		output[3]= new TypedIOPort(this, "outputS", false, true);
		output[4]= new TypedIOPort(this, "outputL", false, true);
		for(int i=0;i<5;i++)output[i].setMultiport(true);

		read = new TypedIOPort[5];
		read[0]= new TypedIOPort(this, "readE", false, true);
		read[1]= new TypedIOPort(this, "readW", false, true);
		read[2]= new TypedIOPort(this, "readN", false, true);
		read[3]= new TypedIOPort(this, "readS", false, true);
		read[4]= new TypedIOPort(this, "readL", false, true);
		for(int i=0;i<5;i++)read[i].setMultiport(true);

		output[0].setTypeAtLeast(inputreq[0]);
		output[1].setTypeAtLeast(inputreq[1]);
		output[2].setTypeAtLeast(inputreq[2]);
		output[3].setTypeAtLeast(inputreq[3]);
		output[4].setTypeAtLeast(inputreq[4]);

		read[0].setTypeAtLeast(ack[0]);
		read[1].setTypeAtLeast(ack[1]);
		read[2].setTypeAtLeast(ack[2]);
		read[3].setTypeAtLeast(ack[3]);
		read[4].setTypeAtLeast(ack[4]);





	}



	/**
	 * Method called once by the director when simulation starts; 
	 * responsible to initialize variables and state machines.
	 */
	public void initialize() throws IllegalActionException {
		super.initialize();

		vcs=inputreq[4].getWidth();
		muxin=new int[5][vcs];
		muxout=new int[5][vcs];
		state=new int[5][vcs];
		packetsize=new int[5][vcs];
		headertimestamp=new Time[5][vcs];

		//all output ports are free upon start-up
		for(int i=0; i<5;i++){

			//frees the allocation table for each input port and virtual channel
			//frees the allocation table for each ack port and virtual channel  
			//resets the state of all input ports
			for(int j=0;j<vcs;j++){
				muxin[i][j]=-1;
				muxout[i][j]=-1;
				state[i][j]=0;
			}


		}




	}



	/**
	 * Implements most of the arbiter functionality: <br>
	 * 1 - check if are there header tokens on any inputs; if so, arbitrate, route, 
	 *     lock output port, set muxin, muxout and size, then update state and 
	 *     store timestamp<br>
	 * 2 - if timestamp of header is older than current time+delay,
	 *     then send token to output <br>
	 * 2 - send payload tokens to output port <br>
	 * 3 - read ack tokens, forward them to the buffer, update states, muxin and muxout if needed,
	 *     decrement size state variable <br>
	 */
	public void fire() throws IllegalActionException {
		super.fire();

		processHeaders();
		processPayload();
		processAcks();
		

	}

	protected  void processHeaders() throws IllegalActionException{	
		/***********************************
		 **********PROCESS HEADERS**********
		 ***********************************/	

		//for each input multiport that has connections
		for(int i=0; i<5;i++){
			//if(_debugging) _debug(getDirector().getModelTime()+" checking in port "+i); 

			if(inputreq[i].getWidth()!=0){	
				//if(_debugging) _debug(getDirector().getModelTime()+" port connected "+i); 

				// for each of input's virtual channels
				for(int j=0;j<vcs;j++){
					//if(_debugging) _debug(getDirector().getModelTime()+" checking virtual channel "+j); 


					if (inputreq[i].hasToken(j)){
						//if(_debugging) _debug(getDirector().getModelTime()+" virtual channel has token "+j); 


						if(state[i][j]==REQUESTING){

							if(_debugging) _debug(getDirector().getModelTime()+" processing requests of port "+i+" "+j); 

							record = (RecordToken)inputreq[i].get(j);
							int dir=route(record);
							if(muxout[dir][j]==-1){
								// routed output port is free for this virtual channel	

								// assign input and virtual channel to the routed output
								muxin[i][j]=dir;
								// lock output port by assigning ack-read link
								muxout[dir][j]=i;
								// updates the state
								state[i][j]=WAITING;
								if(_debugging) _debug(getDirector().getModelTime()+" setting waiting state to port "+i+" "+j); 


								// register packet size 
								packetsize[i][j]=((IntToken)record.get("size")).intValue()+2; // to account for "header" and "size" flits  
								if(_debugging) _debug(getDirector().getModelTime()+" storing packet size "+packetsize[i][j]); 

								// calculate and store header release timestamp
								// triggers a fire for that time

								IntToken cyc = (IntToken)routingdelay.getToken();
								DoubleToken per =(DoubleToken)period.getToken();

								//calculate arbitration time by multiplying the cycles by the period
								Time routingdelaytime=new Time(getDirector());
								for (int n=0;n<cyc.intValue();n++)
									routingdelaytime = routingdelaytime.add(per.doubleValue());


								headertimestamp[i][j]=getDirector().getModelTime().add(routingdelaytime);

								getDirector().fireAt(this, headertimestamp[i][j]);

								

							}


						}

						else if(state[i][j]==WAITING){

							int dif=getDirector().getModelTime().compareTo(headertimestamp[i][j]); 
							if(dif==0 | dif==1){
								//waiting is over
								state[i][j]=SENDING;
								if(_debugging) _debug(getDirector().getModelTime()+" setting sending state to port "+i+" "+j); 

							}
							else inputreq[i].get(j); // discards token

						}


					}

				}
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
			// and discard all others

			boolean sent = false;


			for(int j=0;j<vcs;j++){


				if(muxout[i][j]!=-1){
					if(inputreq[muxout[i][j]].hasToken(j)&& state[muxout[i][j]][j]==SENDING){

						if(!sent){
							output[i].send(j, inputreq[muxout[i][j]].get(j));

							sent=true;

							if(_debugging) _debug(getDirector().getModelTime()+" sending flit from port "+muxout[i][j]+" vc "+ j +" via output "+i); 


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
			for(int j=0;j<ack[i].getWidth();j++){
				if (ack[i].hasToken(j)){
					BooleanToken boo=(BooleanToken)ack[i].get(j);
					boolean b = (boo).booleanValue();
					if(b){
						//send ack notification back to buffer 
						int respectiveinput=muxout[i][j];
						read[respectiveinput].send(j,boo);
						if(_debugging) _debug(getDirector().getModelTime()+" sending ack from port "+i+" "+j+" to input "+respectiveinput); 


						//decrement size
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
				}

			}
		}

	}



	/**
	 *  Routes a given packet based on its XY destination coordinates;
	 *  currently implements the XY routing algorithm of Hermes.
	 *  @param record the header token that contains the destination of the packet 
	 *  @return the output port resulted from the routing algorithm
	 */


	protected int route(RecordToken record) throws IllegalActionException{
		int tx = ((IntToken)record.get("x")).intValue();
		int ty = ((IntToken)record.get("y")).intValue();
		int lx = ((IntToken)x.getToken()).intValue();
		int ly = ((IntToken)y.getToken()).intValue();
		if (lx==tx && ly==ty) return LOCAL;
		else if (lx>tx) return WEST;
		else if (lx<tx) return EAST;
		else if (ly<ty) return NORTH;
		else return SOUTH;
	}





}
