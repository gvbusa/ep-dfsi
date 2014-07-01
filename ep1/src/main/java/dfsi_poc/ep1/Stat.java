package dfsi_poc.ep1;

import com.pubsubstore.revs.core.Dimension;

public class Stat {
	private Dimension dim;
	private long count;

	public Dimension getDim() {
		return dim;
	}

	public void setDim(Dimension dim) {
		this.dim = dim;
	}

	public long getCount() {
		return count;
	}

	public void setCount(long count) {
		this.count = count;
	}

}
