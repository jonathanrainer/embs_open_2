package lsi.noc.application;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.data.IntToken;
import ptolemy.data.expr.Parameter;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;


/**
 * @version 1.0 (York, 18/08/2009)
 * @author Leandro Soares Indrusiak, Sanna Maatta
 *
 * Abstract superclass of all elements attached to a platform model.
 * Models its ID and the mesh coordinates of the platform element it is attached to.
 * TODO: Currently handles bi-dimensional platforms only. Adding additional dimensions should be trivial.
 * 
 *
*/

@SuppressWarnings("serial")
public abstract class PlatformCommunicationInterface extends TypedAtomicActor {



	protected PlatformCommunicationInterface(CompositeEntity container, String name) throws NameDuplicationException, IllegalActionException  {
		super(container, name);


		// Each platform element has a unique id
		actorID = new Parameter(this, "actorID");
		actorID.setTypeEquals(BaseType.INT);
		actorID.setToken("1");


		// X-coordinate of the router the platform element is attached to
		addressX = new Parameter(this, "X");
		addressX.setTypeEquals(BaseType.INT);
		addressX.setToken("0");

		// Y-coordinate of the router the platform element is attached to
		addressY = new Parameter(this, "Y");
		addressY.setTypeEquals(BaseType.INT);
		addressY.setToken("0");

	}

	
	
	
    /**
     * Method returns the X-coordinate of the address of the router the platform element is connected to
     * @return
     * @throws IllegalActionException
     */
    public int getAddressX() throws IllegalActionException {
        
        return ((IntToken) addressX.getToken()).intValue();
      
    }
    
    /**
     * Method returns the Y-coordinate of the address of the router the platform element is connected to
     * @return
     * @throws IllegalActionException
     */
    public int getAddressY() throws IllegalActionException {
      
        return ((IntToken) addressY.getToken()).intValue();
      
    }

	
	
	
    /**
     * Changes attribute values
     */
    public void attributeChanged(Attribute attribute)
    throws IllegalActionException {
        
       if (attribute == actorID) {
    
           int newActorID = ((IntToken) actorID.getToken()).intValue();
           actorID_ = newActorID;   
                                      
       } else if (attribute == addressX) {
           
           int newAddressX = ((IntToken) addressX.getToken()).intValue();
           addressX_ = newAddressX;
           
       } else if (attribute == addressY) {
           
           int newAddressY = ((IntToken) addressY.getToken()).intValue();
           addressY_ = newAddressY;
           
       } else {                            
           super.attributeChanged(attribute);
       }  
   }
	

	public Parameter actorID;
	public Parameter addressX;
	public Parameter addressY;
	protected int actorID_, addressX_, addressY_; 

}
