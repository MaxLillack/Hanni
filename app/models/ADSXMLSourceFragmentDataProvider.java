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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import ca.usask.cs.srlab.simcad.dataprovider.AbstractFragmentDataProvider;
import ca.usask.cs.srlab.simcad.dataprovider.xml.IXMLFragmentDataProviderTransformer;
import ca.usask.cs.srlab.simcad.dataprovider.xml.XMLMultiSourceFragmentDataProviderConfiguration;
import ca.usask.cs.srlab.simcad.model.CloneFragment;
import ca.usask.cs.srlab.simcad.util.PropsUtil;

public class ADSXMLSourceFragmentDataProvider extends AbstractFragmentDataProvider{
	
	private String originalSourcePath;
	
	@SuppressWarnings("unused")
	private ADSXMLSourceFragmentDataProvider(){
	}
	
	private static ExecutorService executor = Executors.newFixedThreadPool(4);
	
	private ConcurrentHashMap<CloneFragment, String> transformedContents = new ConcurrentHashMap<>();
	
	public ADSXMLSourceFragmentDataProvider(XMLMultiSourceFragmentDataProviderConfiguration dataProviderConfig, String originalSourcePath){
		super(dataProviderConfig);
		this.originalSourcePath = originalSourcePath;
	}
	
	public class FragmentTask implements Callable<List<CloneFragment>>
	{
		private String fileName;
		private List<Element> sourceList;
		private List<String> originalSourceLines;
		private int index;
		private ConcurrentHashMap<CloneFragment, String> transformedContents;
		
		public FragmentTask(String fileName,
				List<Element> sourceList,
				List<String> originalSourceLines,
				int index,
				ConcurrentHashMap<CloneFragment, String> transformedContents)
		{
			this.fileName = fileName;
			this.sourceList = sourceList;
			this.originalSourceLines = originalSourceLines;
			this.index = index;
			this.transformedContents = transformedContents;
		}
		
		@Override
		public List<CloneFragment> call() throws Exception {
			List<CloneFragment> result = buildFragments(fileName, sourceList, originalSourceLines, index);
			return result;
		}
		
		private List<CloneFragment> buildFragments(
				String fileName,
				List<Element> sourceList,
				List<String> originalSourceLines,
				int index) {
			
			List<CloneFragment> cloneFragmentList = new LinkedList<CloneFragment>();
			Integer minSizeOfGranularity = PropsUtil.getMinSizeOfGranularity();
			
			StringBuilder allContent = new StringBuilder();
			StringBuilder allTransformedContent = new StringBuilder();
			
			Element first = sourceList.get(0);
			Element last = sourceList.get(sourceList.size() - 1);
			
			int totalStartline = Integer.parseInt(first.getAttribute("POS").split(" ")[0]) % 1000000;
			int totalEndline = (Integer.parseInt(last.getAttribute("POS").split(" ")[1]) % 1000000) - 1;
			
			
			for(Element element : sourceList) {
	            String[] positions = element.getAttributes().getNamedItem("POS").getFirstChild().getNodeValue().split(" ");
	            int startline = Integer.parseInt(positions[0]) % 1000000;
	            int endline = Math.min((Integer.parseInt(positions[1]) % 1000000) - 1, originalSourceLines.size());

				StringBuilder sb = new StringBuilder();
				for(int lineIndex = startline; lineIndex <= endline; lineIndex++)
				{
					sb.append(originalSourceLines.get(lineIndex - 1) + "\n");
				}
				
				String content = sb.toString();
				String transformedContent = content;
				String[] transformedContentLines = content.split("\\n");
				

				
				NodeList cobolLineElements = element.getElementsByTagName("rf.unclassified_cobol_line");
				for(int i = 0; i < cobolLineElements.getLength(); i++)
				{
					Element cobolLineElement = (Element) cobolLineElements.item(i);
					NodeList lineFragmentElements = cobolLineElement.getElementsByTagName("fr.line_fragment");
					for(int j = 0; j < lineFragmentElements.getLength(); j++)
					{
						Element lineFragmentElement = (Element) lineFragmentElements.item(j);
						if(lineFragmentElement.getElementsByTagName("sl.fragment").getLength() > 0)
						{
							Matcher matcher = Pattern.compile("\\S+").matcher(lineFragmentElement.getTextContent().trim());
							
							while (matcher.find()) {
								String token = matcher.group();
								token = token.replace(".", "");
								if(!isKeyword(token)) {
									String[] tokenPositions = lineFragmentElement.getAttribute("POS").split(" ");
									int line = Integer.parseInt(tokenPositions[0]) % 1000000;
									if(line - startline < transformedContentLines.length) {
										transformedContentLines[line - startline] = StringUtils.replaceOnce(transformedContentLines[line - startline], token, StringUtils.leftPad("x", token.length()));							
									}
								}
							}
						}
						
					}
				}
				
				List<Element> elements = new ArrayList<>();
				
				NodeList macroParameterElements = element.getElementsByTagName("fr.macro_parameter");
				for(int i = 0; i < macroParameterElements.getLength(); i++)
				{
					Element e = (Element) macroParameterElements.item(i);
					if(e.getElementsByTagName("sl.parameter").getLength() > 0) {
						elements.add(e);
					}
				}
				
				NodeList lineParameterElements = element.getElementsByTagName("fr.line_parameter_fragment");
				for(int i = 0; i < lineParameterElements.getLength(); i++)
				{
					Element e = (Element) lineParameterElements.item(i);
					if(e.getElementsByTagName("sl.parameter").getLength() > 0) {
						elements.add(e);
					}
				}
				
				
				for(Element e : elements)
				{
					String[] tokenPositions = e.getAttribute("POS").split(" ");
					int line = Integer.parseInt(tokenPositions[0]) % 1000000;
					
					int startCol = Integer.parseInt(tokenPositions[2]);
					int endCol = Integer.parseInt(tokenPositions[3]);
					if(endCol == 0) {
						if(transformedContentLines.length > 0 && (line - startline) >= 0 && ((line - startline) < transformedContentLines.length)) { 
							endCol = transformedContentLines[line - startline].length();
						}
					}
					if(endCol > 0 && (line - startline) < transformedContentLines.length) {
						String temp = transformedContentLines[line - startline];
						// Tabs are counted as 8 spaces (important for correct column numbers)
						temp = temp.replaceAll("\t", StringUtils.repeat(' ', 8));
						// Insert placeholder
						temp = StringUtils.overlay(temp, StringUtils.repeat("y", endCol - startCol), startCol, endCol);
						// remove variable_flag
						temp = temp.replace("#", "");
						transformedContentLines[line - startline] = temp;
					}
					
				}			
				
				transformedContent = StringUtils.join(transformedContentLines, "\n");
				
				allContent.append(content);
				allTransformedContent.append(transformedContent);
			}
			
			
			String content = allContent.toString();
			String transformedContent = allTransformedContent.toString();
			
			if(CloneFragment.computeActualLineOfCode(content) >= minSizeOfGranularity) {
				CloneFragment cloneFragment = createNewCloneFragment(fileName, Integer.toString(totalStartline), Integer.toString(totalEndline), content, transformedContent, index);
				transformedContents.put(cloneFragment, transformedContent);
				cloneFragmentList.add(cloneFragment);
			}

			
			return cloneFragmentList;
		}
		
		private boolean isKeyword(String token) {
			return Keywords.keywords.contains(token);
		}

		public ConcurrentHashMap<CloneFragment, String> getTransformedContents() {
			return transformedContents;
		}
		
		
	}
	
	public List<CloneFragment> extractFragments(){
		
		String dataSource = applyDataTransformation();
		
		List<String> originalSourceLines = null;
		try {
			originalSourceLines = Files.readAllLines(Paths.get(originalSourcePath), StandardCharsets.ISO_8859_1);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		
		List<CloneFragment> cloneFragmentList = new LinkedList<CloneFragment>();
		
		if(originalSourceLines != null) {
			File fileName = new File(dataSource);
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try{
			DocumentBuilder db = dbf.newDocumentBuilder();
			
	        InputStream inputStream= new FileInputStream(fileName);
	        Reader reader = new InputStreamReader(inputStream,"UTF-8");
	        InputSource is = new InputSource(reader);
	        is.setEncoding("UTF-8");
	
	        Document doc = db.parse(is);
	//        doc.getDocumentElement().normalize();
			
			Element root = doc.getDocumentElement();
			
			int index = 0;
			
			NodeList toplevelElements = root.getElementsByTagName("rf.commands").item(0).getChildNodes();
			
			List<FragmentTask> tasks = new ArrayList<FragmentTask>();
			
			List<Element> nodes = new LinkedList<Element>();
			for(int i = 0; i < toplevelElements.getLength(); i++)
			{
				if(toplevelElements.item(i).getNodeType() == Node.ELEMENT_NODE) {
					Element element = (Element) toplevelElements.item(i);
					
					String nodeName = element.getNodeName();
					boolean isIfStatement = false;
					
					if(nodeName.equals("fr.ads_macro_statement")) {
						// check for if statement
						NodeList ifElements = null;

						ifElements = element.getElementsByTagName("rf.macro_if_statement"); 

						if(ifElements.getLength() > 0) {
							isIfStatement = true;
						}
					}
					
					// Add new fragment, reset
					if(nodeName.equals("fr.ads_processor") || isIfStatement)
					{
						if(!nodes.isEmpty()) {
//							cloneFragmentList.addAll(buildFragments(dataSource, nodes, originalSourceLines, index++));
							tasks.add(new FragmentTask(dataSource, nodes, originalSourceLines, index++, transformedContents));
						}
//						cloneFragmentList.addAll(buildFragments(dataSource, Collections.singletonList(element), originalSourceLines, index++));
						tasks.add(new FragmentTask(dataSource, Collections.singletonList(element), originalSourceLines, index++, transformedContents));
						nodes = new LinkedList<Element>();
					} else {
						// Append to fragment
						nodes.add(element);
					}
				}
			}
			
			if(!nodes.isEmpty())
			{
//				cloneFragmentList.addAll(buildFragments(dataSource, nodes, originalSourceLines, index++));
				tasks.add(new FragmentTask(dataSource, nodes, originalSourceLines, index++, transformedContents));
			}
			
			for(FragmentTask task : tasks)
			{
				List<CloneFragment> result = task.call();
				cloneFragmentList.addAll(result);
				transformedContents.putAll(task.getTransformedContents());
			}
			
			/*
			List<Future<List<CloneFragment>>> runTasks = executor.invokeAll(tasks);
			System.out.println(runTasks.size() + " tasks run");
			for(Future<List<CloneFragment>> task : runTasks)
			{
				List<CloneFragment> result = task.get();
				cloneFragmentList.addAll(result);
			}
			System.out.println("cloneFragmentList: " + cloneFragmentList.size());
			*/
			
			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		return cloneFragmentList;
	}

	public ConcurrentHashMap<CloneFragment, String> getTransformedContents() {
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
