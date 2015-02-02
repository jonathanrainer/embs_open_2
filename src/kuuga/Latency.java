package kuuga;

public class Latency {
	
	private double best;
	private double worst;
	private double accumulator;
	private int comm_number;
	
	public Latency(double best, double worst, double accumulator, int comm_number)
	{
		this.best = best;
		this.worst = worst;
		this.accumulator = accumulator;
		this.comm_number = comm_number;
	}
	
	public void setBest(double new_best)
	{
		if(new_best < best)
		{
			best = new_best;
		}
	}
	
	public void setWorst(double new_worst)
	{
		if(new_worst > worst)
		{
			worst = new_worst;
		}
	}
	
	public void addAccumulator(double new_latency)
	{
		accumulator += new_latency;
		comm_number++;
	}
	
	public double calculateAverage()
	{
		return accumulator/comm_number;
	}

	public double getBest() {
		return best;
	}

	public double getWorst() {
		return worst;
	}
	

}
