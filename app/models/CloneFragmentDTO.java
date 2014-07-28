package models;

public class CloneFragmentDTO {
	private String content;
	private int startLine;
	private int endLine;
	private String fragmentId;
	private int groupId;
	private String fileName;
	
	public CloneFragmentDTO(String content, int startLine, int endLine, String fragmentId, int groupId, String fileName)
	{
		this.content = content;
		this.startLine = startLine;
		this.endLine = endLine;
		this.fragmentId = fragmentId;
		this.groupId = groupId;
		this.fileName = fileName;
	}

	public int startLine() { return startLine; }
	
	public int endLine() { return endLine; }
	
	public String getContent() {
		return content;
	}

	public String getFragmentId() {
		return fragmentId;
	}
	
	public int getGroupId()
	{
		return groupId;
	}
	
	public String getFileName() { return fileName; }
}
