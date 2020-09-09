package writers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Tag;

public class IndexWriter extends BaseWriter {
	
	class Alphabetizer implements Comparator<String>
	{
		public int compare(String o1, String o2) {
			// TODO Auto-generated method stub
			int tagEnd = o1.indexOf(">");
			String inside1 = o1.substring( tagEnd + 1, tagEnd + 2 );
			tagEnd = o2.indexOf(">");
			String inside2 = o2.substring( tagEnd + 1, tagEnd + 2 );
			
			// compare first letter for symbols
			// check alphanumericity of both strings
			if( inside1.matches( "[a-zA-Z0-9 ]+" ) && ! inside2.matches("[a-zA-Z0-9 ]+") )
			{
				// if only the second is a symbol
				return 1;
			}
			else if( inside2.matches( "[a-zA-Z0-9 ]+" ) && ! inside1.matches("[a-zA-Z0-9 ]+") )
			{
				// if only the first is a symbol
				return -1;
			}
			
			// compare whole strings, starting with words
			inside1 = o1.substring( o1.indexOf(">") + 1, o1.length() );
			
			inside2 = o2.substring( o2.indexOf(">") + 1, o2.length() );
			// do a normal string comparison on the insides (not the html tags)
			return String.CASE_INSENSITIVE_ORDER.compare( inside1, inside2 );
		}
		
	}
	
	HashMap<String, ArrayList<String>> sections;
	
	TemplateWriter templateWriter;
	
	public IndexWriter(){
		sections = new HashMap<String, ArrayList<String>>();
		templateWriter = new TemplateWriter();
	}
	
	public void write() throws IOException{
		templateWriter.write("index.template.html", getCompressedSections(), "index.html");
		templateWriter.write("index.alphabetical.template.html", getAlphabetical(), "alpha.html");
	}
	
	private HashMap<String, String> getCompressedSections () {
		HashMap<String, String> ret = new HashMap<String, String>();
		
		for (String key : sections.keySet()) {
			
			String value = "";
			//make things alphabetical in their sections
			Collections.sort( sections.get(key), new Alphabetizer() );
			
			for (String s : sections.get(key))
			{
				if ( !value.contains(s) )
				{					
					value = value.concat("\n").concat(s);
				}
			}
			ret.put(key, value);
		}
		return ret;
	}
	
	private HashMap<String, String> getAlphabetical(){
		HashMap<String, String> ret = new HashMap<String, String>();
		ArrayList<String> all = new ArrayList<String>();
		
		for(String key : sections.keySet()){
			for( String s : sections.get(key)){
				if( ! all.contains(s) )
				{
					all.add(s);
				}
			}
		}
		
		Collections.sort(all, new Alphabetizer() );
		
		String value = "";
		float numColumns = 3.0f;
		int currentColumn = 0;
		float perColumn = all.size()/numColumns;
		
		float counter = 0.0f;
		
		String startString = all.get(0);
		
		String currentLetterGroup = startString.substring( startString.indexOf(">") + 1, startString.indexOf(">") + 2 );
		// find the stuff inside brackets
		
		for(String s : all){			
			counter++;
			int tagEnd = s.indexOf(">");
			String currentLetter = s.substring( tagEnd + 1, tagEnd + 2 );
			
			
			if( currentLetter.matches( "[a-zA-Z0-9]") && ! currentLetter.equalsIgnoreCase( currentLetterGroup ) )
			{	// add a break at letter change
				value = value.concat("<br/>");
				currentLetterGroup = currentLetter;
			}
			
			value = value.concat( s );
			
			if( counter >= perColumn ){
				counter = 0;
				currentColumn++;
				ret.put("c"+currentColumn, value);
				value = "";
			}
		}
		if(counter != 0){
			currentColumn++;
			ret.put("c"+currentColumn, value);
		}
		ret.put("isAlphabetical", "true");
		return ret;
	}
	
	public void addItem ( ProgramElementDoc doc, Tag webref ) throws IOException {
		
		String name = getName(doc);
		String anchor = getAnchorFromName(name);
		String category = getCategory(webref);
		String subcategory = getSubcategory(webref);
		String classes = isModeSupported(doc, MODE_JAVASCRIPT) ? "" : "no-js";
		
		addItem(category, subcategory, name, anchor, classes);
	}
	
	public void addItem (String category, String subcategory, String name, String anchor, String classes ) throws IOException{
		String key = getKey(category, subcategory);
		HashMap<String, String> vars = new HashMap<String, String>();
		vars.put("anchor", anchor);
		vars.put("name", name);
		vars.put("classes", classes);
		
		String value = templateWriter.writePartial("index.entry.partial.html", vars);
		if( sections.containsKey(key))
		{
			sections.get(key).add(value);
		} else {
			ArrayList<String> a = new ArrayList<String>();
			a.add(value);
			sections.put(key, a);			
		}
	}
	
	private String getKey(String category, String subCategory){
		if( !subCategory.equals("") ){
			String sub = subCategory.replaceAll(" ", "_");
			return category.toLowerCase() + ":" + sub.toLowerCase();
		}
		return category.toLowerCase();
	}
	
}
