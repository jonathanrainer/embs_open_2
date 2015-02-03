package kuuga;

import java.util.HashMap;
import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.DoubleToken;
import ptolemy.data.IntToken;
import ptolemy.data.RecordToken;
import ptolemy.data.StringToken;
import ptolemy.data.type.BaseType;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;

public class ResultAnalyser extends TypedAtomicActor{
	
	protected TypedIOPort input; // receives tasks
	protected TypedIOPort output; // dispatches tasks
	
	protected HashMap<Integer, Latency> end_to_end_latency_tracker;
	protected HashMap<Integer, Latency> comms_latency_tracker;
	
	public ResultAnalyser(CompositeEntity container, String name)
			throws NameDuplicationException, IllegalActionException 
	{
		super(container, name);

		input = new TypedIOPort(this, "input", true, false);
		input.setTypeEquals(BaseType.GENERAL);
		input.setMultiport(true);

		output = new TypedIOPort(this, "output", false, true);
		output.setMultiport(true);
		output.setTypeEquals(BaseType.GENERAL);
		
		end_to_end_latency_tracker = new HashMap<Integer, Latency>();
		comms_latency_tracker = new HashMap<Integer, Latency>();
	}
	
	public void fire() throws IllegalActionException
	{
		for (int i = 0; i < input.getWidth(); i++) 
		{
			if (input.hasToken(i)) {
				// reads task
				RecordToken task = (RecordToken) input.get(i);
				int id = ((IntToken) task.get("id")).intValue();
				double comm_latency = commLatencyCalculation(task);
				latencyUpdate(comms_latency_tracker, id, comm_latency);
				double end_to_end_latency = endToEndLatencyCalculation(task);
				latencyUpdate(end_to_end_latency_tracker, id, end_to_end_latency);
				Latency comms_latency_record = comms_latency_tracker.get(id);
				Latency end_to_end_latency_record = end_to_end_latency_tracker.get(id);
				String id_string = "########## Task ID: " + id + " ##########\n";
				String end_to_end_latency_string = generateOutputString(end_to_end_latency_record, "End To End");
				String comm_latency_string = generateOutputString(comms_latency_record, "Communication");
				StringToken out_s = new StringToken(id_string + end_to_end_latency_string + comm_latency_string);
				output.send(0,task);
				output.send(0,out_s);
			}
		}
	}
	
	private double endToEndLatencyCalculation(RecordToken task)
	{
		double comm_finish_time = ((DoubleToken) task.get("commfinishtime")).doubleValue();
		double release_time = ((DoubleToken) task.get("releasetime")).doubleValue();
		return comm_finish_time - release_time;
	}
	
	private double commLatencyCalculation(RecordToken task)
	{
		double comm_finish_time = ((DoubleToken) task.get("commfinishtime")).doubleValue();
		double comp_finish_time = ((DoubleToken) task.get("compfinishtime")).doubleValue();
		return comm_finish_time - comp_finish_time;
	}
	
	private void latencyUpdate(HashMap<Integer, Latency> latency_store, int id, double new_latency)
	{
		if(latency_store.get(id) == null)
		{
			latency_store.put(id, new Latency(new_latency, new_latency, new_latency, 1));
		}
		else
		{
			Latency latency_record = latency_store.get(id);
			latency_record.addAccumulator(new_latency);
			latency_record.setBest(new_latency);
			latency_record.setWorst(new_latency);
		}
	}
	
	private String generateOutputString(Latency latency_record, String title)
	{
		String LatencyString = "########## " + title + " ########## \nBest Latency: " + latency_record.getBest()
				+ "\nWorst Latency: " + latency_record.getWorst() + 
				"\nAverage Latency: " + latency_record.calculateAverage() + "\n";
		return LatencyString;
	}

}
