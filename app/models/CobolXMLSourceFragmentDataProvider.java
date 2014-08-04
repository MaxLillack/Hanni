package models;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

import ca.usask.cs.srlab.simcad.dataprovider.AbstractFragmentDataProvider;
import ca.usask.cs.srlab.simcad.dataprovider.xml.IXMLFragmentDataProviderTransformer;
import ca.usask.cs.srlab.simcad.dataprovider.xml.XMLMultiSourceFragmentDataProviderConfiguration;
import ca.usask.cs.srlab.simcad.model.CloneFragment;
import ca.usask.cs.srlab.simcad.util.PropsUtil;

public class CobolXMLSourceFragmentDataProvider extends AbstractFragmentDataProvider{
	
	private String originalSourcePath;
	
	private HashMap<CloneFragment, String> transformedContents = new HashMap<>();
	
	@SuppressWarnings("unused")
	private CobolXMLSourceFragmentDataProvider(){
	}
	
	private int index;
	
	public CobolXMLSourceFragmentDataProvider(XMLMultiSourceFragmentDataProviderConfiguration dataProviderConfig, String originalSourcePath, int startIndex){
		super(dataProviderConfig);
		this.originalSourcePath = originalSourcePath;
		this.index = startIndex;
	}
	
	public List<CloneFragment> extractFragments(){
		
		String dataSource = applyDataTransformation();
		
		List<String> originalSourceLines = new ArrayList<String>();
		Map<Integer, String> lineNumberToMacroId = new HashMap<Integer, String>();
		
		
		try {
			List<String> originalSourceLinesPre = Files.readAllLines(Paths.get(originalSourcePath), StandardCharsets.ISO_8859_1);
			int i = 1;
			for(String line : originalSourceLinesPre)
			{
				// parse macroid in cobol meta-data
				String code = StringUtils.right(line.trim(), 8);
				String macroid = code.substring(0, 2);
				lineNumberToMacroId.put(i++, macroid);
				
				originalSourceLines.add(line.substring(6, Math.min(line.length(), 72)));
			}
			
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		
		List<CloneFragment> cloneFragmentList = new LinkedList<CloneFragment>();
		Integer minSizeOfGranularity = PropsUtil.getMinSizeOfGranularity();
		
		File fileName = new File(dataSource);
		
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try{
		DocumentBuilder db = dbf.newDocumentBuilder();
		
		//Document doc = db.parse(fileName);
		//doc.getDocumentElement().normalize();
        InputStream inputStream= new FileInputStream(fileName);
        Reader reader = new InputStreamReader(inputStream,"UTF-8");
        InputSource is = new InputSource(reader);
        is.setEncoding("UTF-8");

        Document doc = db.parse(is);
        doc.getDocumentElement().normalize();
		
		Element root = doc.getDocumentElement();
		  
		NodeList sourceList = root.getElementsByTagName("paragraph");
		  
		if(sourceList.getLength()>0) {
			//NodeList sourceList = nl.item(0).getChildNodes();
			
			Integer items = index;
			
			for(int i = 0; i < sourceList.getLength(); i++){
				Node source = sourceList.item(i);
				if (source.getNodeType() == Node.ELEMENT_NODE) {
					String file = dataSource;
					String startline = source.getAttributes().getNamedItem("from-line").getFirstChild().getNodeValue();
					String endline = source.getAttributes().getNamedItem("to-line").getFirstChild().getNodeValue();
					
					
					StringBuilder sb = new StringBuilder();
					for(int lineIndex = Integer.parseInt(startline); lineIndex <= Integer.parseInt(endline); lineIndex++)
					{
						sb.append(originalSourceLines.get(lineIndex - 1) + "\n");
					}
					
					String content = sb.toString();
					String transformedContent = content;
					
					Element element = (Element) source;					
					transformedContent = replaceValues(transformedContent, element.getElementsByTagName("cobolWord"), "x", Integer.parseInt(startline));
					transformedContent = replaceValues(transformedContent, element.getElementsByTagName("literal"), "y", Integer.parseInt(startline));
					
					if(!(CloneFragment.computeActualLineOfCode(content) < minSizeOfGranularity)) {
						CloneFragment cloneFragment = createNewCloneFragment(file, startline, endline, content, transformedContent, items++);
						cloneFragmentList.add(cloneFragment);
						transformedContents.put(cloneFragment, transformedContent);
					}
					
					// Add sub fragments based on macro source
					sb = new StringBuilder();
					String macroidOld = lineNumberToMacroId.get(Integer.parseInt(startline));
					
					int start = Integer.parseInt(startline);
					
					for(int lineIndex = Integer.parseInt(startline); lineIndex <= Integer.parseInt(endline); lineIndex++)
					{
						String macroid = lineNumberToMacroId.get(lineIndex);
						
						// ignore non-user macros
						if(macroid.equals("SP") || macroid.equals("PR")) {
							macroid = macroidOld;
						}
						
						if(macroid.equals(macroidOld)) {
							sb.append(originalSourceLines.get(lineIndex - 1) + "\n");
						} 
						
						if(!macroid.equals(macroidOld) || lineIndex == Integer.parseInt(endline)) {
							// macroid change -> add fragment
							content = sb.toString();
							transformedContent = content;
							
							element = (Element) source;					
							transformedContent = replaceValues(transformedContent, element.getElementsByTagName("cobolWord"), "x", Integer.parseInt(startline));
							transformedContent = replaceValues(transformedContent, element.getElementsByTagName("literal"), "y", Integer.parseInt(startline));
							
							if(!(CloneFragment.computeActualLineOfCode(content) < minSizeOfGranularity)) {
								CloneFragment cloneFragment = createNewCloneFragment(file, Integer.toString(start), Integer.toString(lineIndex), content, transformedContent, items++);
								cloneFragmentList.add(cloneFragment);
							}
							// clear StringBuilder
							sb.setLength(0);
							sb.append(originalSourceLines.get(lineIndex - 1) + "\n");
							start = lineIndex;
						}
						macroidOld = macroid;
					}
				}
			}

			System.out.println("Total Cobol items processed: "+items);
		  }
		} 
		catch(SAXParseException e)
		{
			System.err.println("Error parsing " + fileName.getName());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return cloneFragmentList;
	}

	private String replaceValues(String transformedContent, NodeList nodes, String replaceWith, int lineOffset) {
		String[] lines = transformedContent.split("\\n");
		
		for(int j = 0; j < nodes.getLength(); j++) {
			Node node = nodes.item(j);
			String text = node.getTextContent().trim();
			
			int lineNumber = Integer.parseInt(node.getAttributes().getNamedItem("from-line").getFirstChild().getNodeValue());
			
			if((lineNumber - lineOffset) < lines.length) {
				String line = lines[lineNumber - lineOffset];
				lines[lineNumber - lineOffset] = StringUtils.replace(line, text, replaceWith);
			}
		}
		
		return StringUtils.join(lines, "\n");
	}

	public HashMap<CloneFragment, String> getTransformedContents() {
		return transformedContents;
	}

	//@Override
	protected String applyDataTransformation() {
		List<IXMLFragmentDataProviderTransformer> dataTransformerList = ((XMLMultiSourceFragmentDataProviderConfiguration)dataProviderConfig).getDataTransformer();
		String dataSource = ((XMLMultiSourceFragmentDataProviderConfiguration)dataProviderConfig).getOriginalSourceXmlFileName();
		for (IXMLFragmentDataProviderTransformer xmlFragmentDataProviderTransformer : dataTransformerList) {
			dataSource = xmlFragmentDataProviderTransformer.transform(dataSource);
		}
		return dataSource;
	}
	
}
