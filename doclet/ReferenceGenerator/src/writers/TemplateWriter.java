package writers;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class TemplateWriter extends BaseWriter {

	public static String varPrefix = "<!-- ";
	public static String varSuffix = " -->";
	static String[] genericFields = {"classname", "returns", "related", "parameters", "syntax", "webcontentpath"};
	static String[] navFields = {"isLibrary", "isAlphabetical", "isLanguage"};

	public TemplateWriter()
	{
	}

	public void write( String templateName, HashMap<String, String> vars, String outputName ) throws IOException
	{
		for(String s : genericFields){
			if( ! vars.containsKey(s)){
				vars.put(s, "");
			}
		}

		int unsetNavCount = 0;
		for(String s : navFields){
			if(!vars.containsKey(s)){
				vars.put(s, "");
				unsetNavCount++;
			}else if(!vars.get(s).equals("")){
				vars.put(s, "class='active'");
			}
		}

		if(unsetNavCount == navFields.length){
			vars.put("isLanguage", "class='active'");
		}

		Boolean written = write( templateName, vars, outputName, false );
		write( templateName, vars, outputName, true );
		if( written && Shared.i().isNoisy() )
		{
			System.out.println("Wrote " + outputName + " from template");
		}
	}

	// returns a relative path to root (e.g. "../../" from "path/to/File.ext", or "" for "File.txt")
	public String getRelativePathToRoot( String path )
	{
		String[] parts = path.split("/");
		String ret = "";
		for( int i = parts.length - 1; i > 0; --i )
		{
			ret += "../";
		}
		return ret;
	}

	private Boolean write( String templateName, HashMap<String, String> vars, String outputName, Boolean isLocal ) throws IOException
	{
		String[] templateFile = FileUtils.loadStrings(Shared.i().TEMPLATE_DIRECTORY() + templateName);
		ArrayList<String> output = new ArrayList<String>();
		vars.put("timestamp", getTimestamp());
		if(isLocal)
		{
			vars.put( "webcontentpath",  getRelativePathToRoot( outputName ) );
			vars.put("jquery", writePartial("jquery.local.partial.html", vars));
			vars.put("navigation", writePartial("nav.local.template.html", vars));
		} else
		{
			vars.put( "webcontentpath",  "/" );
			vars.put("jquery", writePartial("jquery.web.partial.html", vars));
			vars.put("navigation", writePartial("nav.web.template.html", vars));
		}

		File f = new File( getWriterPath( outputName, isLocal ) );

		if( ! f.exists() )
		{
			BufferedWriter out = makeWriter(outputName, isLocal);

			for( String line : templateFile )
			{
				// check if it contains a variable we want to replace, then replace it
				line = writeLine(line, vars, false);
				output.add(line);
			}
			for( String line : output )
			{
				out.write(line+"\n");
			}
			out.close();

			return true;
		}
		else
		{
			return false;
		}
	}

	public String writePartial( String templateName, HashMap<String, String> vars )
	{	//use to write partials to be assigned to vars keys
		String[] templateFile = FileUtils.loadStrings(Shared.i().TEMPLATE_DIRECTORY()+templateName);
		String ret = "";

		for( String line : templateFile )
		{
			line = writeLine(line, vars, false );
			ret = ret.concat(line+"\n");
		}

		return ret;
	}

	public String writeLoop( String templateName, ArrayList<HashMap<String, String>> varSet )
	{
		return writeLoop(templateName, varSet, "\n");
	}

	public String writeLoop( String templateName, ArrayList<HashMap<String, String>> varSet, String separator )
	{
		String[] templateFile = FileUtils.loadStrings(Shared.i().TEMPLATE_DIRECTORY()+templateName);
		String ret = "";

		int index = 0;
		for( HashMap<String, String> vars : varSet )
		{
			index++;
			for( String line : templateFile )
			{
				ret = ret + writeLine(line, vars, (index == varSet.size()) ) + separator;
			}
		}
		if(ret.endsWith(separator)){
			ret = ret.substring(0, ret.lastIndexOf(separator));
		}
		return ret;
	}

	public ArrayList<String> writeLoopSyntax(String templateName, ArrayList<HashMap<String, String>> varSet)
	{
		String[] templateFile = FileUtils.loadStrings(Shared.i().TEMPLATE_DIRECTORY()+templateName);
		ArrayList<String> syntaxList = new ArrayList<String>();

		int index = 0;
		for( HashMap<String, String> vars : varSet )
		{
			index++;
			for( String line : templateFile )
			{
				syntaxList.add(writeLine(line, vars, (index == varSet.size()) ));
			}
		}

		return syntaxList;
	}


	public ArrayList<String> writeFields(String templateName, ArrayList<HashMap<String, String>> varSet)
	{
		String[] templateFile = FileUtils.loadStrings(Shared.i().TEMPLATE_DIRECTORY()+templateName);
		ArrayList<String> syntaxList = new ArrayList<String>();

		int index = 0;
		for( HashMap<String, String> vars : varSet )
		{
			index++;
			for( String line : templateFile )
			{
				syntaxList.add(writeLine(line, vars, (index == varSet.size()) ));
			}
		}

		return syntaxList;
	}

	private String writeLine(String line, HashMap<String, String> map, boolean isFinalLine )
	{
		for( String key : map.keySet())
		{
			if(line.contains(key))
			{
				String value = map.get(key);
				value = value.replace("$", "\\$");
				// what variable in html the value should replace
				String var = varPrefix + key + varSuffix;

				// place our value into the html
				line = line.replaceFirst(var, value);

				// find html that requires presence or lack of value
				String requireStart = varPrefix + "require:" + key + varSuffix;
				String requireEnd = varPrefix + "end" + varSuffix;
				String requireAbsenceStart = varPrefix + "unless:" + key + varSuffix;
				String unlessLastStart = varPrefix + "unless:last_fragment" + varSuffix;

				if(value.equals(""))
				{	//remove html around things that are absent (like images)
					while(line.contains(requireStart))
					{
						String sub = line.substring(line.indexOf(requireStart), line.indexOf(requireEnd) + requireEnd.length());
						line = line.replace(sub, "");
					}
				}
				else
				{
					// remove things that should only exist in absence of this value
					while(line.contains(requireAbsenceStart))
					{
						String sub = line.substring(line.indexOf(requireAbsenceStart), line.indexOf(requireEnd) + requireEnd.length());
						line = line.replace(sub, "");
					}
				}

				if( isFinalLine )
				{
					while(line.contains(unlessLastStart))
					{
						String sub = line.substring(line.indexOf(unlessLastStart), line.indexOf(requireEnd) + requireEnd.length());
						line = line.replace(sub, "");
					}
				}

				// finally, remove all the meta junk
				line = line.replaceAll(requireStart, "");
				line = line.replaceAll(requireEnd, "");
				line = line.replaceAll(requireAbsenceStart, "");
				line = line.replaceAll(unlessLastStart, "");
			}
		}
		// Strip trailing whitespace (trim() removes beginning and end)
		return line.replaceAll("\\s+$", "");
	}

}
