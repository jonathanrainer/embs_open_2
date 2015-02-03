package agito;

import java.util.Comparator;

public class PacketPriorityComparator implements Comparator<Packet> {

	public int compare(Packet arg0, Packet arg1) {
		return Integer.compare(arg0.getPriority(), arg1.getPriority());
	}

}
