package writers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.sun.javadoc.Doc;
import com.sun.javadoc.ProgramElementDoc;

public class Shared {
//	what we're looking for
	private static Shared instance;
	private String webrefTagName = "webref";
	private String seeAlsoTagName = "see_external";
	private String coreClassName = "PApplet";
	private ArrayList<String> descriptionSets;

	//where things go
	private String outputDirectory = "web_reference";
	private String localOutputDirectory = "local_reference";
	private String imageDirectory = "images";
	private String fileExtension = ".html";

	//where things come from
	private String templateDirectory = "templates";
	private String exampleDirectory = "web_examples";
	private String includeDirectory = "include";
	private String jsonDirectory ="../../../processing-website/json/";

	boolean noisy = false;
	public ArrayList<String> corePackages;
	public ArrayList<String> rootClasses;

	private Shared(){
		corePackages = new ArrayList<String>();
		rootClasses = new ArrayList<String>();
		descriptionSets = new ArrayList<String>();

		addDescriptionTag("description");
	}

	public static Shared i()
	{
		if(instance == null)
		{
			instance = new Shared();
		}
		return instance;
	}

	public String getWebrefTagName(){
		return webrefTagName;
	}

	public String getSeeAlsoTagName()
	{
		return seeAlsoTagName;
	}

	public void setIncludeDirectory( String s )
	{
		includeDirectory = s;
	}

	public String getIncludeDirectory()
	{
		return includeDirectory + "/";
	}

	public void setWebrefTagName(String webrefTagName)
	{
		this.webrefTagName = webrefTagName;
	}
	public void setCoreClassName(String coreClassName)
	{
		this.coreClassName = coreClassName;
	}
	public String getCoreClassName()
	{
		return coreClassName;
	}

	public void addDescriptionTag(String s) {
		System.out.println( "Added description tag: " + s );
		descriptionSets.add( "/root/"+s );
		descriptionSets.add( "/root/js_mode/"+s );
	}

	public ArrayList<String> getDescriptionSets() {
		return descriptionSets;
	}

	public void setOutputDirectory(String outputDirectory) {
		this.outputDirectory = outputDirectory;
	}
	public String getOutputDirectory() {
		return outputDirectory;
	}
	public void setFileExtension(String fileExtension) {
		this.fileExtension = fileExtension;
	}
	public String getFileExtension() {
		return fileExtension;
	}
	public void setTemplateDirectory(String templateDirectory) {
		this.templateDirectory = templateDirectory;
	}
	public String getTemplateDirectory() {
		return templateDirectory;
	}
	public String TEMPLATE_DIRECTORY(){
		return templateDirectory + "/";
	}

	public void setExampleDirectory(String exampleDirectory) {
		this.exampleDirectory = exampleDirectory;
	}
	public String getExampleDirectory() {
		return exampleDirectory;
	}
	public String getJSONDirectory(){
		return jsonDirectory + "/";
	}

	public void setImageDirectory(String imageDirectory) {
		this.imageDirectory = imageDirectory;
	}

	public String getImageDirectory(){
		return imageDirectory + "/";
	}
	public void setLocalOutputDirectory(String localOutputDirectory) {
		this.localOutputDirectory = localOutputDirectory;
	}

	public String getLocalOutputDirectory()
	{
		return localOutputDirectory + "/";
	}

	public String OUTPUT_DIRECTORY()
	{
		return outputDirectory + "/";
	}

	public boolean isCore(ProgramElementDoc doc){
		return corePackages.contains(doc.containingPackage().name());
	}

	public boolean isWebref(ProgramElementDoc doc){
		return doc.tags(webrefTagName).length > 0;
	}

	public boolean isRootLevel(ProgramElementDoc doc){
		if(doc.isClass() || doc.isInterface()){
			return rootClasses.contains(doc.name());
		} else {
			return rootClasses.contains(doc.containingClass().name());
		}
	}

	public boolean isNoisy(){
		return noisy;
	}

	public void setNoisy(boolean b){
		noisy = b;
	}

	public void createOutputDirectory(String dir){
		System.out.println("Creating output directory: " + dir );
		File f = new File(getLocalOutputDirectory() + dir);
		f.mkdirs();

		f = new File(OUTPUT_DIRECTORY() + dir);
		f.mkdirs();
	}

	public static Document loadXmlDocument( String path )
	{
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		DocumentBuilder builder;
		Document doc = null;
		try {
			builder = factory.newDocumentBuilder();
			doc = builder.parse( path );
		} catch (ParserConfigurationException e) {
			System.out.println("Failed to parse " + path );
			System.out.println( e.getLocalizedMessage() );
		} catch (SAXException e) {
			System.out.println("Failed to parse " + path );
			System.out.println( e.getLocalizedMessage() );
		} catch (IOException e) {
			System.out.println("Failed to parse " + path );
			System.out.println( e.getLocalizedMessage() );
		}

		return doc;
	}

	public void createBaseDirectories(){
		File f = new File(getLocalOutputDirectory());
		f.mkdirs();

		f = new File(OUTPUT_DIRECTORY());
		f.mkdirs();
	}

	public boolean shouldOmit(Doc doc){
		if( doc.tags("nowebref").length > 0 )
		{
			return true;
		}
		if( doc.tags("notWebref").length > 0 )
		{
			return true;
		}
		// if none found, we should include
		return false;
	}
}
