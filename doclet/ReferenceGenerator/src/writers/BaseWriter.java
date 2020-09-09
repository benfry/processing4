package writers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.ExecutableMemberDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;

import org.json.*;

public class BaseWriter {
	// Some utilities

	public final static String MODE_JAVASCRIPT = "js";
	public final static String jsonDir = "../../../processing-website/json/";

	public BaseWriter()
	{

	}

	protected static BufferedWriter makeWriter(String anchor) throws IOException
	{
		return makeWriter(anchor, false);
	}

	protected static String getWriterPath( String anchor, Boolean isLocal )
	{
		if (!isLocal) {
			return Shared.i().getOutputDirectory() + "/" + anchor;
		} else
		{
			return Shared.i().getLocalOutputDirectory() + anchor;
		}
	}

	protected static BufferedWriter makeWriter(String anchor, Boolean isLocal) throws IOException {
		FileWriter fw = new FileWriter( getWriterPath( anchor, isLocal ) );

		return new BufferedWriter(fw);
	}

	protected static String getAnchor(ProgramElementDoc doc)
	{
		String ret = getAnchorFromName(getName(doc));

		if(doc.containingClass() != null && !Shared.i().isRootLevel(doc.containingClass()))
		{
			ret = doc.containingClass().name() + "_" + ret;
		}

		if(!Shared.i().isCore(doc)){
			//add package name to anchor
			String[] parts = doc.containingPackage().name().split("\\.");
			String pkg = parts[parts.length-1] + "/";
			ret = "libraries/" + pkg + ret;
		}

		return ret;
	}

	protected static String getLocalAnchor(ProgramElementDoc doc)
	{
		String ret = getAnchorFromName(getName(doc));
		if(doc.containingClass() != null){
			ret = doc.containingClass().name() + "_" + ret;
		}

		return ret;
	}

	protected static String getReturnTypes(MethodDoc doc)
	{
		String ret = nameInPDE(doc.returnType().toString());
		if(doc.containingClass() != null)
		{
			for(MethodDoc m : doc.containingClass().methods())
			{
				if( m.name().equals(doc.name()) && m.returnType() != doc.returnType() )
				{
					String name = getSimplifiedType( nameInPDE(m.returnType().toString()) );
					if( ! ret.contains( name ) )
					{ // add return type name if it's not already included
						ret += ", " + name;
					}
				}
			}
		}

		// add "or" (split first to make sure we don't mess up the written description)
		ret = ret.replaceFirst( ",([^,]+)$", ", or$1" );
		if( ! ret.matches(".+,.+,.+") )
		{
			ret = ret.replace( ",", "" );
		}

		return ret;
	}

	protected static String getSimplifiedType( String str )
	{
		if( str.equals("long") ){ return "int"; }
		if( str.equals("double") ){ return "float"; }

		return str;
	}

	protected static String getName(Doc doc) { // handle
		String ret = doc.name();
		if(doc instanceof MethodDoc)
		{
			ret = ret.concat("()");
		} else if (doc.isField()){
			// add [] if needed
			FieldDoc d = (FieldDoc) doc;
			ret = ret.concat(d.type().dimension());
		}
		return ret;
	}

	protected static String getAnchorFromName(String name){
		// change functionName() to functionName_
		if( name.endsWith("()") ){
			name = name.replace("()", "_");
		}
		// change "(some thing)" to something
		if( name.contains("(") && name.contains(")") ){
			int start = name.lastIndexOf("(") + 1;
			int end = name.lastIndexOf(")");
			name = name.substring(start, end);
			name = name.replace(" ", "");
		}
		// change thing[] to thing
		if( name.contains("[]")){
			name = name.replaceAll("\\[\\]", "");
		}
		// change "some thing" to "some_thing.html"
		name = name.replace(" ", "_");
		return name;
	}

	static protected String getBasicDescriptionFromSource(ProgramElementDoc doc) {
		return getBasicDescriptionFromSource(longestText(doc));
	}

	static protected String getBriefDescriptionFromSource(ProgramElementDoc doc) {
		Tag[] sta = doc.tags("brief");
		if(sta.length > 0){
			return sta[0].text();
		}
		return getBasicDescriptionFromSource(doc);
	}

	static protected String getWebDescriptionFromSource(ProgramElementDoc doc) {
		Tag[] sta = doc.tags("webDescription");
		if(sta.length > 0){
			return sta[0].text();
		}
		return getBasicDescriptionFromSource(doc);
	}

	static protected String getWebBriefFromSource(ProgramElementDoc doc) {
		Tag[] sta = doc.tags("webBrief");
		if(sta.length > 0){
			return sta[0].text();
		}
		return getBasicDescriptionFromSource(doc);
	}

	static protected String longestText(ProgramElementDoc doc){
		if(Shared.i().isWebref(doc)){
			//override longest rule if the element has an @webref tag
			return doc.commentText();
		}

		String s = doc.commentText();
		if( doc.isMethod() ){
			for(ProgramElementDoc d : doc.containingClass().methods()){
				if(d.name().equals(doc.name() ) ){
					if(d.commentText().length() > s.length()){
						s = d.commentText();
					}
				}
			}
		} else if(doc.isField()){
			for(ProgramElementDoc d : doc.containingClass().fields()){
				if(d.name().equals(doc.name() ) ){
					if(d.commentText().length() > s.length()){
						s = d.commentText();
					}
				}
			}
		}
		return s;
	}

	static protected String getBasicDescriptionFromSource(String s){
		String[] sa = s.split("(?i)(<h\\d>Advanced:?</h\\d>)|(=advanced)");
		if (sa.length != 0)
			s = sa[0];
		return s;
	}

	static protected String getAdvancedDescriptionFromSource(ProgramElementDoc doc) {
		return getAdvancedDescriptionFromString(longestText(doc));
	}
	static private String getAdvancedDescriptionFromString(String s) {
		String[] sa = s.split("(?i)(<h\\d>Advanced:?</h\\d>)|(=advanced)");
		if (sa.length > 1)
			s = sa[1];
		return s;
	}

	protected static HashMap<String, String> getDefaultDescriptionVars ()
	{
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("description title", "Description");
		vars.put("description text", "");
		return vars;
	}

	protected static String getTimestamp() {
		Calendar now = Calendar.getInstance();
		Locale us = new Locale("en");

		return now.getDisplayName(Calendar.MONTH, Calendar.LONG, us)
				+ " "
				+ now.get(Calendar.DAY_OF_MONTH)
				+ ", "
				+ now.get(Calendar.YEAR)
				+ " "
				+ FileUtils.nf(now.get(Calendar.HOUR), 2)
				+ ":"
				+ FileUtils.nf(now.get(Calendar.MINUTE), 2)
				+ ":"
				+ FileUtils.nf(now.get(Calendar.SECOND), 2)
				+ now.getDisplayName(Calendar.AM_PM, Calendar.SHORT, us)
						.toLowerCase()
				+ " "
				+ TimeZone.getDefault().getDisplayName(
						TimeZone.getDefault().inDaylightTime(now.getTime()),
						TimeZone.SHORT, us);
	}

	/*
	 * Get all the syntax possibilities for a method
	 */
	protected static ArrayList<HashMap<String, String>> getSyntax(MethodDoc doc, String instanceName) throws IOException
	{
		TemplateWriter templateWriter = new TemplateWriter();
		ArrayList<HashMap<String, String>> ret = new ArrayList<HashMap<String,String>>();
		

		for( MethodDoc methodDoc : doc.containingClass().methods() )
		{
			if(Shared.i().shouldOmit(methodDoc)){
				continue;
			}
			if( methodDoc.name().equals(doc.name() ))
			{
				HashMap<String, String> map = new HashMap<String, String>();
				map.put("name", methodDoc.name());
				map.put("object", instanceName);

				ArrayList<HashMap<String, String>> parameters = new ArrayList<HashMap<String,String>>();
				for( Parameter p : methodDoc.parameters() )
				{
					HashMap<String, String> paramMap = new HashMap<String, String>();
					paramMap.put("parameter", p.name());
					parameters.add(paramMap);
				}
				String params = templateWriter.writeLoop("method.parameter.partial.html", parameters, ", ");

				map.put("parameters", params);
				if( ! ret.contains(map) )
				{
					//don't put in duplicate function syntax
					ret.add(map);
				}
			}
		}

		return ret;
	}

	private static String removePackage(String name)
	{ // keep everything after the last dot
		if( name.contains(".") )
		{ return name.substring( name.lastIndexOf(".") + 1 ); }
		return name;
	}

	private static String nameInPDE(String fullName)
	{
		if( fullName.contains("<") && fullName.endsWith(">") )
		{	// if this type uses Java generics
			String parts[] = fullName.split("<");
			String generic = removePackage( parts[0] );
			String specialization = removePackage( parts[1] );
			specialization = specialization.substring( 0, specialization.length() - 1 );
			return generic + "&lt;" + specialization + "&gt;";
		}
		return removePackage( fullName );
	}

	protected static String getUsage(ProgramElementDoc doc){
		Tag[] tags = doc.tags("usage");
		if(tags.length != 0){
			return tags[0].text();
		}
		tags = doc.containingClass().tags("usage");
		if(tags.length != 0){
			return tags[0].text();
		}
		// return empty string if no usage is found
		return "";
	}

	protected static String getInstanceName(ProgramElementDoc doc){
		Tag[] tags = doc.containingClass().tags("instanceName");
		if(tags.length != 0){
			return tags[0].text().split("\\s")[0];
		}
		return "";
	}

	protected static String getInstanceDescription(ProgramElementDoc doc){
		Tag[] tags = doc.containingClass().tags("instanceName");
		if(tags.length != 0){
			String s = tags[0].text();
			return s.substring(s.indexOf(" "));
		}
		return "";
	}

	protected static ArrayList<JSONObject>  getParameters(MethodDoc doc) throws IOException{
		//get parent
		ClassDoc cd = doc.containingClass();
		ArrayList<JSONObject> ret = new ArrayList<JSONObject>();

		if(!Shared.i().isRootLevel(cd)){
			//add the parent parameter if this isn't a function of PApplet
			JSONObject parent = new JSONObject();
			try
			{
				ArrayList<String> paramType = new ArrayList<String>();
				paramType.add(cd.name());
				parent.put("name", getInstanceName(doc));
				parent.put("type", paramType);
				parent.put("description",getInstanceDescription(doc));
				ret.add(parent);
			} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}
		}

		//get parameters from this and all other declarations of method
		for( MethodDoc m : cd.methods() ){
			if(Shared.i().shouldOmit(m)){
				continue;
			}
			if(m.name().equals(doc.name())){
				ret.addAll(parseParameters(m));
			}
		}

		removeDuplicateParameters(ret);

		return ret;
	}


	protected static ArrayList<JSONObject>  getParameters(ClassDoc doc) throws IOException{
		ArrayList<JSONObject> ret = new ArrayList<JSONObject>();
		for( ConstructorDoc m : doc.constructors() ){
			if(Shared.i().shouldOmit(m)){
				continue;
			}
			ret.addAll(parseParameters(m));
		}
		removeDuplicateParameters(ret);

		return ret;
	}

	protected static void removeDuplicateParameters(ArrayList<JSONObject> ret){
		// combine duplicate parameter names with differing types
		try
			{
		for(JSONObject parameter : ret)
		{
			String desc = parameter.getString("description");
			JSONArray paramArray = parameter.getJSONArray("type");
			if(!desc.endsWith(": "))
			{
				// if the chosen description has actual text
				// e.g. float: something about the float
				for(JSONObject parameter2 : ret)
				{
					String desc2 = parameter2.getString("description");

					if(parameter2.getString("name").equals( parameter.getString("name") ) )
					{
						// freshen up our variable with the latest description						

						// for (int i=0; i < parameter2.get("type").size(); i++) {
						// 	System.out.println("par " + parameter2.get("type").get(i));
						// }
						
						JSONArray paramArray2 = parameter2.getJSONArray("type");
						String addItem = "";

						for(int i = 0; i < paramArray.length(); i++)
						{
							for(int j = 0; j < paramArray2.length(); j++) {

								if ( !paramArray.getString(i).equals(paramArray2.getString(j))) {
									addItem = paramArray2.getString(j);
								}
							}
      						
						}

						if (addItem != "") {
							paramArray.put(addItem);
						}
					}
				}
			}

			ArrayList<String> newList = new ArrayList<String>();

			for (int i = 0; i < paramArray.length(); i++) {
				if (!newList.contains(paramArray.getString(i))) {
					newList.add(paramArray.getString(i));
				}
			}

			parameter.put("type", newList);
		}
		//remove parameters without descriptions
		for( int i=ret.size()-1; i >= 0; i-- )
		{
			if(ret.get(i).getString("description").equals(""))
			{
				ret.remove(i);
			}
		}

		} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}
	}

	protected static ArrayList<JSONObject> parseParameters(ExecutableMemberDoc doc){
		ArrayList<JSONObject> paramList = new ArrayList<JSONObject>();
		for( Parameter param : doc.parameters()){
			String type = getSimplifiedType( nameInPDE(param.type().toString()) );
			String name = param.name();
			String desc = "";

			for( ParamTag tag : doc.paramTags() ){
				if(tag.parameterName().equals(name)){
					desc = desc.concat( tag.parameterComment() );
				}
			}

			JSONObject paramJSON = new JSONObject();

			try
			{
				ArrayList<String> paramType = new ArrayList<String>();
				paramType.add(type);
				paramJSON.put("name", name);
				paramJSON.put("type", paramType);
				paramJSON.put("description", desc);
			} catch (JSONException ex) 
      		{
      			ex.printStackTrace();
      		}

      		paramList.add(paramJSON);

		}

		return paramList;
	}

	protected static ArrayList<SeeTag> getAllSeeTags( ProgramElementDoc doc )
	{
		ArrayList<SeeTag> ret = new ArrayList<SeeTag>();
		ClassDoc cd = doc.containingClass();
		if( cd != null && doc.isMethod() )
		{	// if there is a containing class, get @see tags for all
			// methods with the same name as this one
			// Fixes gh issue 293
			for( MethodDoc m : cd.methods() )
			{
				if(m.name().equals(doc.name()))
				{
					for( SeeTag tag : m.seeTags() )
					{
						ret.add( tag );
					}
				}
			}
		}
		else
		{	// if no containing class (e.g. doc is a class)
			// just grab the see tags in the class doc comment
			for( SeeTag tag : doc.seeTags() )
			{
				ret.add( tag );
			}
		}
		return ret;
	}

	protected static ArrayList<String> getRelated( ProgramElementDoc doc ) throws IOException
	{
		TemplateWriter templateWriter = new TemplateWriter();
		ArrayList<HashMap<String, String>> vars = new ArrayList<HashMap<String,String>>();
		ArrayList<String> related = new ArrayList<String>();

		// keep track of class members so that links to methods in e.g. PGraphics
		// that are copied into PApplet are correctly linked.
		HashMap<String, ProgramElementDoc> classMethods = new HashMap<String, ProgramElementDoc>();
		HashMap<String, ProgramElementDoc> classFields = new HashMap<String, ProgramElementDoc>();
		if( doc.isMethod() || doc.isField() )
		{	// fill our maps
			ClassDoc containingClass = doc.containingClass();
			for( MethodDoc m : containingClass.methods() ) {
				if( needsWriting( m ) ) {
					classMethods.put( m.name(), m );
				}
			}
			for( FieldDoc f : containingClass.fields() ) {
				if( needsWriting( f ) ) {
					classFields.put( f.name(), f );
				}
			}
		}

		// add link to each @see item
		for( SeeTag tag : getAllSeeTags( doc ) )
		{
			HashMap<String, String> map = new HashMap<String, String>();
			ProgramElementDoc ref = tag.referencedClass();
			if( tag.referencedMember() != null )
			{
				ref = tag.referencedMember();
				if( ref.isMethod() && classMethods.containsKey( ref.name() ) ) {
					// link to the member as it is in this class, instead of
					// as it is in another class
					ProgramElementDoc prior = classMethods.get( ref.name() );
					ref = prior;
				}
				else if ( ref.isField() && classFields.containsKey( ref.name() ) ) {
					ProgramElementDoc prior = classFields.get( ref.name() );
					ref = prior;
				}
			}
			if( needsWriting( ref ) )
			{ // add links to things that are actually written
				map.put("anchor", getAnchor( ref ));
				related.add(getAnchor(ref));
			}
		}

		// add link to each @see_external item
		for( Tag tag : doc.tags( Shared.i().getSeeAlsoTagName() ) )
		{
			// get xml for method
			String filename = tag.text() + ".json";
			String basePath = Shared.i().getJSONDirectory();
			File f = new File( basePath + filename );

			if( ! f.exists() )
			{
				basePath = Shared.i().getIncludeDirectory();
				f = new File( basePath + filename );
			}

			if( f.exists() )
			{
				Document xmlDoc = Shared.loadXmlDocument( f.getPath() );
				XPathFactory xpathFactory = XPathFactory.newInstance();
				XPath xpath = xpathFactory.newXPath();

				try
				{
					String name = (String) xpath.evaluate("//name", xmlDoc, XPathConstants.STRING);
					// get anchor from original filename
					String path = f.getAbsolutePath();
					String anchorBase = path.substring( path.lastIndexOf("/")+1, path.indexOf(".xml"));
					if( name.endsWith("()") )
					{
						if( !anchorBase.endsWith("_" ) )
						{
							anchorBase += "_";
						}
					}
					String anchor = anchorBase;

					// get method name from xml
					// get anchor from method name
					HashMap<String, String> map = new HashMap<String, String>();
					map.put( "anchor", anchor );

					related.add( anchor );
				} catch (XPathExpressionException e)
				{
					e.printStackTrace();
				}
			}
		}

		return related;
	}

	protected static String getEvents(ProgramElementDoc doc){
		return "";
	}

		/**
	 *	Modes should support all API, so if XML not explicitly states "not supported", then assume it does.
	 */
	protected static boolean isModeSupported ( ProgramElementDoc doc, String mode ) {

		return true;
	}

	protected static boolean needsWriting(ProgramElementDoc doc){
		if( (doc != null) && Shared.i().isWebref(doc) )
		{
			return true;
		}
		return false;
	}

	public static String getCategory(Tag webref){
		String firstPart = webref.text().split("\\s")[0];
		String[] parts = firstPart.split(":");
		if( parts.length > 1 ){
			return parts[0];
		}
		return firstPart;
	}

	public static String getSubcategory(Tag webref){
		String firstPart = webref.text().split("\\s")[0];
		String[] parts = firstPart.split(":");
		if( parts.length > 1 ){
			return parts[1];
		}
		return "";
	}

}
