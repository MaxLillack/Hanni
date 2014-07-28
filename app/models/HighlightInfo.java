package models;

public class HighlightInfo {
	public String fragmentId;
	public int relativeLineNumber;
	public int groupId;
	
	public HighlightInfo(String fragmentId, int relativeLineNumber, int groupId) {
		this.fragmentId = fragmentId;
		this.relativeLineNumber = relativeLineNumber;
		this.groupId = groupId;
	}
}
