package agito;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;

import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;

public class Packet {
	
	private ArrayList<Packet> interference;
	private boolean active;
	private double active_time;
	private int remaining_payload;
	private ArrayList<Point> route;
	private int priority;
	private int hop_num;
	
	public Packet(RecordToken task, HashMap<Integer, Point> app_platform_mapping)
	{
		interference = new ArrayList<Packet>();
		active = false;
		Point source = app_platform_mapping.get(((IntToken) task.get("id")).intValue());
		route = constructRoute(source.x, source.y, ((IntToken) task.get("x")).intValue(),
				((IntToken) task.get("y")).intValue());
		priority = ((IntToken) task.get("priority")).intValue();
		active_time = 0.0;
		hop_num = route.size()-1;
		remaining_payload = (((IntToken) task.get("size")).intValue()+2 * hop_num);

	}
	
	private ArrayList<Point> constructRoute(int src_x, int src_y, 
			int dest_x, int dest_y)
	{
		ArrayList<Point> potential_route = new ArrayList<Point>(); 
		for(int i = src_x; i< dest_x; i++)
		{
			Point routePointX = new Point(i,0);
			potential_route.add(routePointX);
		}
		for(int j=src_y; j<dest_y; j++)
		{
			Point routePointY = new Point(dest_x,j);
			potential_route.add(routePointY);
		}
		return potential_route;
		
	}

	public ArrayList<Point> getRoute() {
		return route;
	}

	public int getPriority() {
		return priority;
	}

	public ArrayList<Packet> getInterference() {
		return interference;
	}

	public boolean isActive() {
		return active;
	}

	public void decreasePayload(int dec_amount) {
		remaining_payload -= dec_amount;
	}

	public double getActiveTime() {
		return active_time;
	}

	public void setActiveTime(double active_time) {
		this.active_time = active_time;
	}

	public int getRemainingPayload() {
		return remaining_payload;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public int getHopNum() {
		return hop_num;
	}	
}
