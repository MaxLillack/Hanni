package models;

import java.util.List;

public class CloneGroup {
	
	private List<CloneFragmentDTO> cloneFragments;
	private String type;
	private int groupId;
	
	public CloneGroup(List<CloneFragmentDTO> cloneFragments, String type, int groupId)
	{
		this.cloneFragments = cloneFragments;
		this.type = type;
		this.groupId = groupId;
	}
	
	public List<CloneFragmentDTO> getCloneFragments()
	{
		return cloneFragments;
	}
	
	public String getType() {
		return type;
	}

	public int getGroupId() {
		return groupId;
	}

}
