package agito;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.RecordToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class NoCReplacement extends TypedAtomicActor {
	
	protected TypedIOPort input; // receives tasks
	protected TypedIOPort output; // dispatches tasks
	
	protected ArrayList<Packet> plist;
	protected HashMap<Integer, Point> app_platform_map;
	
	protected double cycle_time = 1/(10e8);
	protected double header_time = 2*cycle_time;
	
	
	public NoCReplacement(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException 
	{
		input = new TypedIOPort(this, "input", true, false);
		input.setTypeEquals(BaseType.GENERAL);
		input.setMultiport(true);
		
		ArrayList<Packet> plist = new ArrayList<Packet>(); 
		initialise_mapping();
	}
	
	public void fire() throws IllegalActionException
	{
		for (int i = 0; i < input.getWidth(); i++) 
		{
			if (input.hasToken(i))
			{
				RecordToken task = (RecordToken) input.get(i);
				Packet new_packet = new Packet(task, app_platform_map);
				if (input.hasToken(i))
				{
					Iterator<Packet> it1 = plist.iterator();
					while(it1.hasNext())
					{
						Packet list_packet = it1.next();
						if(emptyRouteIntersection(list_packet.getRoute(), new_packet.getRoute()))
						{
							if(new_packet.getPriority() < list_packet.getPriority())
							{
								new_packet.getInterference().add(list_packet);
							}
							else
							{
								list_packet.getInterference().add(new_packet);
							}
						}
					}
				}
				plist.add(new_packet);
				Collections.sort(plist, new PacketPriorityComparator());
				requestUpdate();
			}
			else
			{
				requestUpdate();
			}
		}
		
	}
	
	private boolean emptyRouteIntersection(
			ArrayList<Point> route_i, ArrayList<Point> route_j)
	{
		ArrayList<Point> route_i_c = new ArrayList<Point>(route_i);
		route_i_c.retainAll(route_j);
		return route_i_c.isEmpty();
	}
	
	
	private void requestUpdate() throws IllegalActionException
	{
		Iterator<Packet> it1 = plist.iterator();
		while(it1.hasNext())
		{
			Packet list_packet = it1.next();
			if(list_packet.isActive())
			{
				double elapsed_time = getDirector().getCurrentTime() - list_packet.getActiveTime();
				list_packet.decreasePayload(sentFlits(elapsed_time));
				list_packet.setActiveTime(getDirector().getCurrentTime());
				if(list_packet.getRemainingPayload() == 0)
				{
					plist.remove(list_packet);
					//TODO Add output code and latency output;
				}
				Iterator<Packet> it2 = list_packet.getInterference().iterator();
				while(it2.hasNext())
				{
					Packet int_packet = it2.next();
					if(int_packet.isActive())
					{
						int_packet.setActive(false);
					}
				}
			}
			else
			{
				if(allInactive(list_packet.getInterference()))
				{
					list_packet.setActive(false);
					list_packet.setActiveTime(getDirector().getCurrentTime());
					double firing_time = getDirector().getCurrentTime() 
							+ noLoadLatency(list_packet.getHopNum(), list_packet.getRemainingPayload());
					getDirector().fireAt(this, firing_time);
				}
			}
		}
	}
	
	private int sentFlits(double time_to_send)
	{
		double time_remaining = time_to_send;
		if(time_to_send == getDirector().getCurrentTime())
		{
			time_remaining = time_remaining - header_time;
		}
		int flits_sent = (int) Math.floor(time_remaining/cycle_time);
		return flits_sent;
	}
	
	private boolean allInactive(ArrayList<Packet> interference)
	{
		Iterator<Packet> intIt = interference.iterator();
		while(intIt.hasNext())
		{
			if(intIt.next().isActive())
			{
				return false;
			}
		}
		return true;
	}
	
	private double noLoadLatency(int hop_num, int flit_count)
	{
		return hop_num * flit_count;
	}
	

	private void initialise_mapping()
	{
		app_platform_map = new HashMap<Integer, Point>();
		app_platform_map.put(0, new Point(0,1));
		app_platform_map.put(1, new Point(0,2));
		app_platform_map.put(2, new Point(2,0));
		app_platform_map.put(3, new Point(1,0));
		app_platform_map.put(4, new Point(1,3));
		app_platform_map.put(5, new Point(0,3));
		app_platform_map.put(6, new Point(2,1));
		app_platform_map.put(7, new Point(3,0));
		app_platform_map.put(8, new Point(1,2));
		app_platform_map.put(9, new Point(3,1));
		app_platform_map.put(10, new Point(0,0));
		app_platform_map.put(11, new Point(3,0));
		app_platform_map.put(12, new Point(1,1));
		app_platform_map.put(13, new Point(1,2));
		app_platform_map.put(14, new Point(0,0));
		app_platform_map.put(15, new Point(2,3));
		app_platform_map.put(16, new Point(2,2));
		app_platform_map.put(17, new Point(0,1));
		app_platform_map.put(18, new Point(3,2));
		app_platform_map.put(19, new Point(3,3));
	}
}
