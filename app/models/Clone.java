package models;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import ca.usask.cs.srlab.simcad.DetectionSettings;
import ca.usask.cs.srlab.simcad.SimCadConstants;
import ca.usask.cs.srlab.simcad.dataprovider.IFragmentDataProvider;
import ca.usask.cs.srlab.simcad.dataprovider.xml.XMLMultiSourceFragmentDataProviderConfiguration;
import ca.usask.cs.srlab.simcad.detection.CloneDetector;
import ca.usask.cs.srlab.simcad.index.ICloneIndex;
import ca.usask.cs.srlab.simcad.index.IndexBuilder;
import ca.usask.cs.srlab.simcad.index.memory.MemoryCloneIndexByGoogleCollection;
import ca.usask.cs.srlab.simcad.model.CloneFragment;
import ca.usask.cs.srlab.simcad.model.CloneSet;
import ca.usask.cs.srlab.simcad.processor.IProcessor;
import ca.usask.cs.srlab.simcad.processor.ProcessorDisptacher;

public class Clone {

	private String cobolSource;
	private List<CloneGroup> macroClones;
	private List<CloneGroup> cobolClones;

	private Map<String, Map<String, String>> codeToMacro;

	public Map<CloneFragmentDTO, CloneFragment> cobolCloneToMacroCode;

	private Map<String, String[]> cobolLines = new HashMap<>();

	public Map<CloneFragment, CloneFragmentDTO> macroFragmentToCloneFragment = new HashMap<>();
	public Map<CloneFragmentDTO, List<CloneFragment>> macroFragmentToPreCloneCobolFragment;

	public Map<CloneFragment, CloneFragmentDTO> cobolFragmentToDetectedClone = new HashMap<>();

	public Config config = ConfigFactory.load();
	
	private Map<String, String> cobolFullSources;

	private DetectionSettings detectionSettings_type123_group = new DetectionSettings(
			SimCadConstants.LANGUAGE_JAVA,
			CloneFragment.CLONE_GRANULARITY_FUNCTION,
			SimCadConstants.CLONE_SET_TYPE_GROUP,
			SimCadConstants.SOURCE_TRANSFORMATION_APPROACH_GENEROUS, false,
			CloneSet.CLONE_TYPE_123);
	private DetectionSettings selected = detectionSettings_type123_group;

	private ICloneIndex cobolIndex;
	private ICloneIndex macroIndex;
	
	private Map<String,String> macroSources;
	private Map<String,String> cobolSources;
	
	private ConcurrentHashMap<CloneFragment, String> transformedCobolFragments = new ConcurrentHashMap<CloneFragment, String>();
	
	public Clone() throws Exception {
		
		macroSources = new HashMap<>();
		
		List<? extends Config> macros = config.getConfigList("macros");
		for(Config macroConfig : macros)
		{
			
			String macro = macroConfig.getString("macro");
			
			if(!new File(macro).exists()) {
				throw new Exception("Macro " + macro + "not found!");
			}
			
			String ast = macroConfig.getString("ast");
			macroSources.put(macro, ast);
		}
		
		if(config.hasPath("allZipsFrom"))
		{
			String allZipsFrom = config.getString("allZipsFrom");
			Collection<File> zips = FileUtils.listFiles(new File(allZipsFrom), new SuffixFileFilter("ZIP"), FileFilterUtils.trueFileFilter());
			for(File file : zips)
			{
				String name = FilenameUtils.getBaseName(file.getName()).split("\\.")[0];
				try(ZipFile zip = new ZipFile(file)) {
					ZipEntry ads = zip.getEntry("ads");
					ZipEntry astadsexp = zip.getEntry("astadsexp");
					
					File adsFile =  new File(config.getString("testDataPath") + "\\" + name);
					File astFile = new File(config.getString("testDataPath") + "\\" + name + "_AST" + ".xml");
					
					FileUtils.copyInputStreamToFile(zip.getInputStream(ads), adsFile);
					FileUtils.copyInputStreamToFile(zip.getInputStream(astadsexp), astFile);
					macroSources.put(adsFile.getAbsolutePath(),	astFile.getAbsolutePath());
				}
			}
		}
		
		cobolSources = new HashMap<>();
		List<String> cobolPaths = config.getStringList("cobolFiles");
		for(String cobolPath : cobolPaths)
		{
			if(!new File(cobolPath).exists()) {
				throw new RuntimeException("Cobol path " + cobolPath + " not found!");
			}
			
			String cobolXmlPath = cobolPath + ".xml";
			cobolSources.put(cobolPath, cobolXmlPath);
			if (!new File(cobolXmlPath).exists()) {
				Koopa.parse(cobolPath, cobolXmlPath);
			}
		}

		cobolFullSources = new HashMap<>();
		for(String cobolPath : cobolSources.keySet()) {
			String cobolSource = FileUtils.readFileToString(new File(cobolPath), "UTF-8");
			cobolFullSources.put(cobolPath, cobolSource);
			String[] lines = cobolSource.split("\n");
			cobolLines.put(FilenameUtils.getBaseName(cobolPath), lines);
		}
		
		String macroClonePath = config.getString("macroClonePath");
		String cobolClonePath = config.getString("cobolClonePath");
	

		createIndexs(cobolSources, macroSources);
		
//		if (!(new File(macroClonePath).exists())) {
		boolean macroClonesFound = detectMacroClones();
//		}
//		if (!(new File(cobolClonePath).exists())) {
		boolean cobolClonesFound = detectCobolClones();
//		}


		if(macroClonesFound) {
			macroClones = loadClones(macroClonePath);
		}
		if(cobolClonesFound) {
			cobolClones = loadClones(cobolClonePath);
		}
		
		if(macroClonesFound && cobolClonesFound) { 
		
			cobolFragmentToDetectedClone = mapCobolClones();
	
			
			List<? extends Config> xrefs = config.getConfigList("xrefPaths");
			Map<String, String> xrefPaths = new HashMap<>();
			for(Config xrefConfig : xrefs)
			{
				String cobolPath = xrefConfig.getString("cobol");
				String xrefPath = xrefConfig.getString("xref");
				xrefPaths.put(cobolPath, xrefPath);
			}
			
			codeToMacro = createCodeToMacro(xrefPaths);
			macroFragmentToPreCloneCobolFragment = mapMacroToCobol();
			cobolCloneToMacroCode = mapCobolToMacro();
		}

	}

	private Map<CloneFragment, CloneFragmentDTO> mapCobolClones() {
		Map<CloneFragment, CloneFragmentDTO> cobolFragmentToDetectedClone = new HashMap<>();
		List<CloneFragment> fragments = loadCobolFragements();
		for (CloneFragment fragment : fragments) {
			for (CloneGroup group : cobolClones) {
				for (CloneFragmentDTO clone : group.getCloneFragments()) {
					if (FilenameUtils.getBaseName(fragment.getFileName()).equals(FilenameUtils.getBaseName(clone.getFileName()))
							&& fragment.getFromLine() == clone.startLine()
							&& fragment.getToLine() == clone.endLine()) {
						cobolFragmentToDetectedClone.put(fragment, clone);
					}
				}
			}
		}
		return cobolFragmentToDetectedClone;
	}

	private int getMacroLine(String cobolFileName, int cobolLineNumber) {
		if(!cobolLines.containsKey(cobolFileName)) {
			throw new RuntimeException("cobolFileName " + cobolFileName + " not found in cobolLines");
		}
		
		String cobolLine = cobolLines.get(cobolFileName)[cobolLineNumber - 1];
		String code = StringUtils.right(cobolLine.trim(), 8);
		int line = Integer.parseInt(code.substring(2));
		return line;
	}

	private String getMacroName(String cobolFileName, int cobolLineNumber) {
		if(!cobolLines.containsKey(cobolFileName)) {
			throw new RuntimeException("cobolFileName " + cobolFileName + " not found in cobolLines");
		}
		
		if(!codeToMacro.containsKey(cobolFileName)) {
			throw new RuntimeException("cobolFileName " + cobolFileName + " not found in codeToMacro");
		}
		
		String cobolLine = cobolLines.get(cobolFileName)[cobolLineNumber - 1];
		String code = StringUtils.right(cobolLine.trim(), 8);
		String macroid = code.substring(0, 2);
		String macroName = codeToMacro.get(cobolFileName).get(macroid);
		return macroName;
	}

	private String getCobolSourceFileName(CloneFragment cloneFragment)
	{
		String filename = null;
		for(Entry<String, String> entry: cobolSources.entrySet())
		{
			if(FilenameUtils.getBaseName(entry.getValue()).equals(FilenameUtils.getBaseName(cloneFragment.getFileName()))) {
				filename = entry.getKey();
			}
		}
		return filename;
	}
	
	private String getCobolSourceFileName(CloneFragmentDTO cloneFragment) throws Exception
	{
		String filename = null;
		for(Entry<String, String> entry: cobolSources.entrySet())
		{
			if(entry.getValue().equals(cloneFragment.getFileName())) {
				filename = entry.getKey();
			}
		}
		
		if(filename == null)
		{
			StringBuilder sb = new StringBuilder();
			for(Entry<String, String> entry: cobolSources.entrySet())
			{
				sb.append(entry.getValue() + "\n");
			}
			throw new Exception(cloneFragment.getFileName() + " not found.\nKnown cobol sources are: \n" + sb.toString());
		}
		
		return filename;
	}
	
	private Map<CloneFragmentDTO, List<CloneFragment>> mapMacroToCobol()
			throws ParserConfigurationException, SAXException, IOException {
		List<CloneFragment> allCobolFragments = loadCobolFragements();
		Map<CloneFragmentDTO, List<CloneFragment>> macroFragmentToPreCloneCobolFragment = new HashMap<>();
		for (CloneGroup group : macroClones) {
			for (CloneFragmentDTO fragment : group.getCloneFragments()) {
				for (CloneFragment cobolFragment : allCobolFragments) {
					boolean found = false;
					for (int i = cobolFragment.getFromLine(); i <= cobolFragment.getToLine(); i++) {
						if (!found) {
							String macroName = getMacroName(FilenameUtils.getBaseName(getCobolSourceFileName(cobolFragment)), i);
							try {
								int macroLine = getMacroLine(FilenameUtils.getBaseName(getCobolSourceFileName(cobolFragment)), i);
								if (FilenameUtils.getBaseName(fragment.getFileName()).startsWith(FilenameUtils.getBaseName(macroName))
										&& fragment.startLine() <= macroLine
										&& fragment.endLine() >= macroLine) {

									// check for CloneFragmentDTO (for highlight
									// info)

									if (!macroFragmentToPreCloneCobolFragment.containsKey(fragment)) {
										macroFragmentToPreCloneCobolFragment.put(fragment, new LinkedList<CloneFragment>());
									}

									// Check if found fragment is better than
									// existing, i.e., if it is smaller and
									// completly covers the current found one

									List<CloneFragment> toDelete = new LinkedList<>();

									Range<Integer> cobolFragmentRange = Range.between(cobolFragment.getFromLine(), cobolFragment.getToLine());

									boolean skip = false;

									for (CloneFragment f : macroFragmentToPreCloneCobolFragment.get(fragment)) {
										Range<Integer> ref = Range.between(f.getFromLine(), f.getToLine());
										if (ref.containsRange(cobolFragmentRange) && cobolFragment != f) {
											toDelete.add(f);
										}
										if (cobolFragmentRange.containsRange(ref)) {
											skip = true;
										}
									}
									macroFragmentToPreCloneCobolFragment.get(fragment).removeAll(toDelete);

									if (!skip) {
										macroFragmentToPreCloneCobolFragment.get(fragment).add(cobolFragment);
									}

									found = true;
								}
							} catch (NumberFormatException ex) {
								// no problem ...
							}
						}
					}
					if (macroFragmentToPreCloneCobolFragment
							.containsKey(fragment)) {
						Collections.sort(macroFragmentToPreCloneCobolFragment.get(fragment),
								new Comparator<CloneFragment>() {
									@Override
									public int compare(CloneFragment o1, CloneFragment o2) {
										return Integer.compare(o1.getFromLine(), o2.getFromLine());
									}
								});
					}

				}

			}
		}
		return macroFragmentToPreCloneCobolFragment;

	}

	private Map<String, Map<String, String>> createCodeToMacro(Map<String, String> xrefPaths)
			throws SAXException, IOException, ParserConfigurationException {
		
		Map<String, Map<String, String>> codeToMacro = new HashMap<>();
		
		for(Entry<String, String> entry : xrefPaths.entrySet()) 
		{
			String cobolPath = entry.getKey();
			String xrefPath = entry.getValue();
			String key = FilenameUtils.getBaseName(cobolPath);
			
			File file = new File(xrefPath);

			codeToMacro.put(key, new HashMap<String,String>());
			
			
			
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document document = db.parse(file);
	
			// Create code map from xref
			NodeList macReferences = document.getElementsByTagName("MacReference");
			for (int i = 0; i < macReferences.getLength(); i++) {
				Element macReference = (Element) macReferences.item(i);
				String macroid = macReference.getAttribute("macroid").split("_")[0];
				String name = macReference.getAttribute("name");
				codeToMacro.get(key).put(macroid, name);
			}
		}
		return codeToMacro;
	}

	private Map<CloneFragmentDTO, CloneFragment> mapCobolToMacro()
			throws Exception {

		Map<CloneFragmentDTO, CloneFragment> cobolFragmentToMacroFragment = new HashMap<>();

		List<CloneFragment> macroFragments = loadMacroFragements();

		for (CloneGroup group : cobolClones) {
			for (CloneFragmentDTO fragment : group.getCloneFragments()) {

				boolean fragmentFound = false;

				for (int i = fragment.startLine(); i <= fragment.endLine(); i++) {
					if (!fragmentFound) {
						String cobolLine = cobolLines.get(FilenameUtils.getBaseName(getCobolSourceFileName(fragment)))[i - 1];
						String code = StringUtils.right(cobolLine.trim(), 8);
						String macroid = code.substring(0, 2);
						String macroName = codeToMacro.get(FilenameUtils.getBaseName(getCobolSourceFileName(fragment))).get(macroid);
						if (macroName != null) {
							int macroLine = Integer.parseInt(code.substring(2));
							// Search for all macro fragments which include this
							// line

							for (CloneFragment macroFragment : macroFragments) {
								if (FilenameUtils.getBaseName(macroFragment.getFileName()).contains(
										FilenameUtils.getBaseName(macroName))
										&& macroFragment.getFromLine() <= macroLine
										&& macroFragment.getToLine() >= macroLine) {
									fragmentFound = true;

									CloneFragmentDTO cloneFragment = getMacroCloneFragmentDTO(macroFragment);
									if (cloneFragment != null) {
										// Mapping of macro fragment to detected
										// macro clone
										macroFragmentToCloneFragment.put(macroFragment, cloneFragment);
									}
									cobolFragmentToMacroFragment.put(fragment, macroFragment);
								}
							}
						}
					}

				}

			}
		}
		
//		System.out.println("macroFragmentToCloneFragment size " + macroFragmentToCloneFragment.size());

		return cobolFragmentToMacroFragment;
	}

	private void createIndexs(Map<String, String> cobolSources, Map<String, String> macroSources) {
		cobolIndex = createCobolIndex(cobolSources);
		macroIndex = createADSIndex(macroSources);
	}

	private boolean detectCobolClones() {
		return detectClones(cobolIndex, "cobol");
	}

	private boolean detectMacroClones() {
		return detectClones(macroIndex, "ads");
	}

	private boolean detectClones(ICloneIndex cloneIndex, String type) {
		
		if(cloneIndex == null)
		{
			throw new IllegalArgumentException("clonesIndex is null (type " + type +")");
		}
		
		CloneDetector cloneDetector = CloneDetector.getInstance(cloneIndex, selected);
		Collection<CloneFragment> candidateFragments = cloneIndex.getAllEntries();
		if(!candidateFragments.isEmpty()) {
			List<CloneSet> result = cloneDetector.detect(candidateFragments);
	
			ProcessorDisptacher pd = ProcessorDisptacher.getInstance();
			IProcessor processor = new XmlOutputProcessor(selected, config.getString("testDataPath"), type);
	
			pd.addProcessor(processor).applyOn(result, selected);
			return true;
		} else {
			System.out.println("no fragments found!");
			return false;
		}
	}
	
	private ExecutorService executor = Executors.newFixedThreadPool(4);
	
	public class CobolFragmentTask implements Callable<List<CloneFragment>>
	{
		private Entry<String, String> entry;
		private int start;
		private ConcurrentHashMap<CloneFragment, String> transformedContents;
		
		
		public CobolFragmentTask(Entry<String, String> entry, int start, ConcurrentHashMap<CloneFragment, String> transformedContents)
		{
			this.entry = entry;
			this.start = start;
			this.transformedContents = transformedContents;
		}
		
		
		@Override
		public List<CloneFragment> call() throws Exception {
			String originalSourcePath = entry.getKey();
			String sourceFragmentFile = entry.getValue();
	
			XMLMultiSourceFragmentDataProviderConfiguration dataProviderConfig = new XMLMultiSourceFragmentDataProviderConfiguration(
					sourceFragmentFile, null,
					"/Volumes/Data/auni_home/test_systems/dnsjava/dnsjava-0-3",
					CloneFragment.CLONE_GRANULARITY_BLOCK);
	
			CobolXMLSourceFragmentDataProvider cloneFragmentDataProvider = new CobolXMLSourceFragmentDataProvider(dataProviderConfig, originalSourcePath, start);
			List<CloneFragment> fragments = cloneFragmentDataProvider.extractFragments();
			transformedContents.putAll(cloneFragmentDataProvider.getTransformedContents());
			return fragments;
		}
		
	
	}

	private ICloneIndex createCobolIndex(Map<String, String> cobolSources) {
		ICloneIndex cloneIndex = new MemoryCloneIndexByGoogleCollection();
		JoininingFragmentProvider dataProvider = new JoininingFragmentProvider();
		
		List<CobolFragmentTask> tasks = new ArrayList<CobolFragmentTask>();
		
		int index = 0;
		
		for(Entry<String, String> entry : cobolSources.entrySet()) {
//			String originalSourcePath = entry.getKey();
//			String sourceFragmentFile = entry.getValue();
//	
//			XMLMultiSourceFragmentDataProviderConfiguration dataProviderConfig = new XMLMultiSourceFragmentDataProviderConfiguration(
//					sourceFragmentFile, null,
//					"/Volumes/Data/auni_home/test_systems/dnsjava/dnsjava-0-3",
//					CloneFragment.CLONE_GRANULARITY_BLOCK);
//	
//			IFragmentDataProvider cloneFragmentDataProvider = new CobolXMLSourceFragmentDataProvider(
//					dataProviderConfig, originalSourcePath);
//			dataProvider.add(cloneFragmentDataProvider.extractFragments());
			
//			Future<List<CloneFragment>> task = executor.submit(new CobolFragmentTask(entry));
			tasks.add(new CobolFragmentTask(entry, index, transformedCobolFragments));
			index += 1000;
		}
		
		for(CobolFragmentTask task : tasks)
		{
			try {
				List<CloneFragment> fragments = task.call();
				dataProvider.add(fragments);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ExecutionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		IndexBuilder indexBuilder = new IndexBuilder(dataProvider);

		indexBuilder.buildCloneIndex(cloneIndex, selected);
		return cloneIndex;
	}

	public String getTransformedCobol(CloneFragment cobolFragment)
	{
		return transformedCobolFragments.get(cobolFragment);
	}
	
	private ICloneIndex createADSIndex(Map<String, String> macroSources) {
		ICloneIndex cloneIndex = new MemoryCloneIndexByGoogleCollection();
		JoininingFragmentProvider dataProvider = new JoininingFragmentProvider();
		
		for(Entry<String, String> entry : macroSources.entrySet())
		{
			String originalSourcePath = entry.getKey();
			String sourceFragmentFile = entry.getValue();
	
			XMLMultiSourceFragmentDataProviderConfiguration dataProviderConfig = new XMLMultiSourceFragmentDataProviderConfiguration(
					sourceFragmentFile, null,
					"/Volumes/Data/auni_home/test_systems/dnsjava/dnsjava-0-3",
					CloneFragment.CLONE_GRANULARITY_BLOCK);
	
			ADSXMLSourceFragmentDataProvider cloneFragmentDataProvider = new ADSXMLSourceFragmentDataProvider(dataProviderConfig, originalSourcePath);
	
			List<CloneFragment> fragments = cloneFragmentDataProvider.extractFragments();
			transformedCobolFragments.putAll(cloneFragmentDataProvider.getTransformedContents());
			dataProvider.add(fragments);
		}
		
		IndexBuilder indexBuilder = new IndexBuilder(dataProvider);

		indexBuilder.buildCloneIndex(cloneIndex, selected);
		return cloneIndex;
	}

	private CloneFragmentDTO getMacroCloneFragmentDTO(
			CloneFragment macroFragment) throws Exception {

		for (CloneGroup group : macroClones) {
			for (CloneFragmentDTO fragment : group.getCloneFragments()) {
				if (FilenameUtils.getBaseName(macroFragment.getFileName())
						.contains(FilenameUtils.getBaseName(fragment.getFileName()))
						&& macroFragment.getFromLine() == fragment.startLine()
						&& macroFragment.getToLine() == fragment.endLine()) {
					return fragment;
				}
			}
		}

		return null;
	}

	private List<CloneGroup> loadClones(String path)
			throws ParserConfigurationException, SAXException, IOException {
		File file = new File(path);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document document = db.parse(file);

		List<CloneGroup> result = new LinkedList<CloneGroup>();

		NodeList cloneGroups = document.getElementsByTagName("clone_group");
		for (int i = 0; i < cloneGroups.getLength(); i++) {
			Element cloneGroup = (Element) cloneGroups.item(i);
			String type = cloneGroup.getAttribute("type");
			int groupId = Integer.parseInt(cloneGroup.getAttribute("groupid"));
			List<CloneFragmentDTO> cloneFragments = new LinkedList<CloneFragmentDTO>();

			NodeList cloneFragmentNodes = cloneGroup
					.getElementsByTagName("clone_fragment");
			for (int j = 0; j < cloneFragmentNodes.getLength(); j++) {
				Element cloneFragmentNode = (Element) cloneFragmentNodes
						.item(j);

				String content = cloneFragmentNode.getTextContent();
				int startLine = Integer.parseInt(cloneFragmentNode
						.getAttribute("startline"));
				int endLine = Integer.parseInt(cloneFragmentNode
						.getAttribute("endline"));
				String fragmentId = cloneFragmentNode.getAttribute("pcid");
				String fileName = cloneFragmentNode.getAttribute("file");
				cloneFragments.add(new CloneFragmentDTO(content, startLine,
						endLine, fragmentId, groupId, fileName));
			}

			// Sort fragments
			// Java8
			// Collections.sort(cloneFragments, (c1, c2) ->
			// Integer.compare(c1.startLine(), c2.startLine()));

			// Java7
			Collections.sort(cloneFragments,
					new Comparator<CloneFragmentDTO>() {
						@Override
						public int compare(CloneFragmentDTO c1,
								CloneFragmentDTO c2) {
							return Integer.compare(c1.startLine(),
									c2.startLine());
						}
					});

			result.add(new CloneGroup(cloneFragments, type, groupId));
		}

		return result;
	}

	public Map<String, String> getMacroSources() throws IOException {
		
		HashMap<String, String> macorSources = new HashMap<>();
		for(String macroPath : macroSources.keySet())
		{
			String macroSource = FileUtils.readFileToString(new File(macroPath), "UTF-8");
			macorSources.put(macroPath, macroSource);
		}
		return macorSources;
	}
	
	public Map<String, String> getCobolSources() throws IOException {
		return cobolFullSources;
	}

	public String getCobolSource() throws IOException {
		return cobolSource;
	}

	public List<CloneGroup> getMacroClones() {
		return macroClones;
	}

	private List<CloneFragmentDTO> getMacroCloneFragments()
	{
		List<CloneFragmentDTO> fragments = new LinkedList<>();
		for(CloneGroup group : getMacroClones())
		{
			fragments.addAll(group.getCloneFragments());
		}
		
		return fragments;
	}
	
	public List<CloneGroup> getCobolClones() {
		return cobolClones;
	}

	public List<CloneFragment> loadCobolFragements() {
		List<CloneFragment> candidateFragments = new LinkedList<CloneFragment>(cobolIndex.getAllEntries());
		
		return candidateFragments;
	}

	public List<CloneFragment> loadMacroFragements() {
		List<CloneFragment> candidateFragments = new LinkedList<CloneFragment>(
				macroIndex.getAllEntries());
		return candidateFragments;
	}

	public int numberofMacroClones() {
		int cloneCount = 0;
		for (CloneGroup group : macroClones) {
			cloneCount += group.getCloneFragments().size();
		}
		return cloneCount;
	}

	public int numberOfMacroCloneGroups() {
		return macroClones.size();
	}

	public List<CloneFragmentDTO> unmappedMacroFragments() {
		List<CloneFragmentDTO> unmappedFragments = new LinkedList<>();
		for (CloneGroup group : macroClones) {
			for (CloneFragmentDTO fragment : group.getCloneFragments()) {
				if (!macroFragmentToPreCloneCobolFragment.containsKey(fragment)) {
					unmappedFragments.add(fragment);
				}
			}
		}
		return unmappedFragments;
	}

	public List<CloneFragment> mappedCobolFragments() {
		List<CloneFragment> mappedCobolFragments = new LinkedList<>();
		for (CloneGroup group : macroClones) {
			for (CloneFragmentDTO fragment : group.getCloneFragments()) {
				List<CloneFragment> cobolFragments = macroFragmentToPreCloneCobolFragment
						.get(fragment);
				if (cobolFragments != null) {
					mappedCobolFragments.addAll(cobolFragments);
				}
			}
		}
		return mappedCobolFragments;
	}

	public List<CloneFragment> nonCloneFragments() {
		List<CloneFragment> nonCloneFragments = new LinkedList<>();
		for (CloneFragment fragment : mappedCobolFragments()) {
			if (!cobolFragmentToDetectedClone.containsKey(fragment)) {
				nonCloneFragments.add(fragment);
			}
		}
		return nonCloneFragments;
	}

	public Map<CloneGroup, Set<CloneGroup>> macroGroupToCobolGroups() {
		Map<CloneGroup, Set<CloneGroup>> groupMapping = new HashMap<>();

		for (CloneGroup group : macroClones) {
			groupMapping.put(group, new HashSet<CloneGroup>());

			for (CloneFragmentDTO fragment : group.getCloneFragments()) {
				List<CloneFragment> cobolFragments = macroFragmentToPreCloneCobolFragment
						.get(fragment);
				if (cobolFragments != null) {
					for (CloneFragment cobolFragment : cobolFragments) {
						CloneFragmentDTO cobolClone = cobolFragmentToDetectedClone
								.get(cobolFragment);
						if (cobolClone != null) {
							// Find group of clone fragment
							CloneGroup cobolGroup = getCobolCloneGroup(cobolClone);
							if (cobolGroup != null) {
								groupMapping.get(group).add(cobolGroup);
							}
						}
					}
				}
			}
		}
		return groupMapping;
	}

	public Set<CloneGroup> macroGroupsWithDifferentMappedGroups() {
		Set<CloneGroup> result = new HashSet<>();
		for (Entry<CloneGroup, Set<CloneGroup>> entry : macroGroupToCobolGroups()
				.entrySet()) {
			if (entry.getValue().size() > 1) {
				result.add(entry.getKey());
			}
		}

		return result;
	}

	private CloneGroup getCobolCloneGroup(CloneFragmentDTO cobolCloneFragment) {
		for (CloneGroup cobolGroup : cobolClones) {
			if (cobolGroup.getCloneFragments().contains(cobolCloneFragment)) {
				return cobolGroup;
			}
		}
		return null;
	}

	public Map<CloneGroup, Set<CloneGroup>> mappedCobolFragmentToMacroGroups() {
		Map<CloneGroup, Set<CloneGroup>> mappedCobolFragmentToMacroGroups = new HashMap<>();

		for (CloneFragment cobolFragment : mappedCobolFragments()) {
			CloneFragmentDTO cloneFragment = cobolFragmentToDetectedClone
					.get(cobolFragment);
			if (cloneFragment != null) {
				CloneGroup cobolGroup = getCobolCloneGroup(cloneFragment);
				if (cobolGroup != null) {
					if (!mappedCobolFragmentToMacroGroups
							.containsKey(cobolGroup)) {
						mappedCobolFragmentToMacroGroups.put(cobolGroup,
								new HashSet<CloneGroup>());
					}
					for (Entry<CloneFragmentDTO, List<CloneFragment>> entry : macroFragmentToPreCloneCobolFragment
							.entrySet()) {
						if (entry.getValue().contains(cobolFragment)) {
							// find group
							for (CloneGroup macroCloneGroup : macroClones) {
								if (macroCloneGroup.getCloneFragments()
										.contains(entry.getKey())) {
									mappedCobolFragmentToMacroGroups.get(
											cobolGroup).add(macroCloneGroup);
								}
							}
						}
					}
				}

			}
		}

		return mappedCobolFragmentToMacroGroups;
	}

	public Set<CloneFragment> potentialType4Clones() {
		// Suche Cobol-Clones, die auf nicht-clone Macrofragments gemappt werden
		// Muss set sein, da natürlich die macro-teile mehrfach gemappt werden
		// könnten
		Set<CloneFragment> type4Clones = new HashSet<CloneFragment>();

		for (CloneGroup group : getCobolClones()) {
			for (CloneFragmentDTO cobolClone : group.getCloneFragments()) {
				CloneFragment macroFragment = cobolCloneToMacroCode.get(cobolClone);
				if (macroFragment != null) {
					if (!macroFragmentToCloneFragment.containsKey(macroFragment)) {
						type4Clones.add(macroFragment);
					}
				}
			}
		}

		return type4Clones;
	}
	
	
	public List<MacroStatistic> getMacroStatistics() throws IOException
	{
		List<MacroStatistic> macroStatistics = new LinkedList<>();
		
		List<CloneFragment> macroFragments = loadMacroFragements();
		List<CloneFragmentDTO> clones = getMacroCloneFragments();
		
		for(Entry<String,String> macroSource : getMacroSources().entrySet())
		{
			
			MacroStatistic statistic = new MacroStatistic();
			
			final String name = FilenameUtils.getBaseName(macroSource.getKey());
			
			statistic.name = name;
			statistic.macroLines = macroSource.getValue().split("\\n").length;
			
			int macroFragmentCount = 0;
			int macroFragmentSize = 0;
			for(CloneFragment macroFragment : macroFragments)
			{
				if(FilenameUtils.getBaseName(macroFragment.getFileName()).startsWith(name))
				{
					macroFragmentCount++;
					macroFragmentSize += (macroFragment.getToLine() - macroFragment.getFromLine()); 
				}
			}
			
			statistic.fragmentCount = macroFragmentCount;
			if(macroFragmentCount > 0) {
				statistic.avgFragmentSize = macroFragmentSize / macroFragmentCount;
			}
			
			statistic.cloneCount = CollectionUtils.countMatches(clones, new Predicate() {
				@Override
				public boolean evaluate(Object arg0) {
					CloneFragmentDTO clone = (CloneFragmentDTO) arg0;
					return FilenameUtils.getBaseName(clone.getFileName()).startsWith(name);
				}
			});
			
			macroStatistics.add(statistic);
		}
		
		return macroStatistics;	
	}
	
	public List<MacroStatistic> getCobolStatistics() throws IOException
	{
		List<MacroStatistic> statistics = new LinkedList<>();
		List<CloneFragment> fragments = loadCobolFragements();
		List<CloneFragmentDTO> clones = getCobolCloneFragments();
		
		
		for(Entry<String, String> entry : cobolFullSources.entrySet())
		{
			MacroStatistic statistic = new MacroStatistic();
			
			final String name = FilenameUtils.getBaseName(entry.getKey());
			
			statistic.name = name;
			statistic.macroLines = entry.getValue().split("\\n").length;
			
			int cobolFragmentCount = 0;
			int cobolFragmentSize = 0;
			for(CloneFragment fragment : fragments)
			{
				// USES CONTAINS !
				if(FilenameUtils.getBaseName(fragment.getFileName()).contains(name))
				{
					cobolFragmentCount++;
					cobolFragmentSize += (fragment.getToLine() - fragment.getFromLine()); 
				}
			}
			
			statistic.fragmentCount = cobolFragmentCount;
			if(cobolFragmentCount > 0) {
				statistic.avgFragmentSize = cobolFragmentSize / cobolFragmentCount;
			}
			
			statistic.cloneCount = CollectionUtils.countMatches(clones, new Predicate() {
				@Override
				public boolean evaluate(Object arg0) {
					CloneFragmentDTO clone = (CloneFragmentDTO) arg0;
					return FilenameUtils.getBaseName(clone.getFileName()).contains(name);
				}
			});
			
			statistics.add(statistic);
		}
		
		return statistics;
	}

	private List<CloneFragmentDTO> getCobolCloneFragments() {
		List<CloneFragmentDTO> fragments = new LinkedList<>();
		for(CloneGroup group : getCobolClones())
		{
			fragments.addAll(group.getCloneFragments());
		}
		
		return fragments;
	}
}
