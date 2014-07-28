package models;

import java.util.LinkedList;
import java.util.List;

import ca.usask.cs.srlab.simcad.dataprovider.AbstractFragmentDataProvider;
import ca.usask.cs.srlab.simcad.model.CloneFragment;

public class JoininingFragmentProvider extends AbstractFragmentDataProvider {

	private List<CloneFragment> list = new LinkedList<CloneFragment>();
	
	public void add(List<CloneFragment> fragments)
	{
		list.addAll(fragments);
	}
	
	@Override
	public List<CloneFragment> extractFragments() {
		return list;
	}

}
