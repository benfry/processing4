package processing.data;

import java.io.PrintWriter;

/**
 * A <b>TableRow</b> object represents a single row of data values, 
 * stored in columns, from a <b>Table</b>.<br />
 * <br />
 * Additional <b>TableRow</b> methods are documented in the 
 * <a href="http://processing.github.io/processing-javadocs/core/">Processing Data Javadoc</a>.
 *
 * @webref data:composite
 * @webBrief Represents a single row of data values, stored in columns, from a <b>Table<b>
 * @see Table
 * @see Table#addRow()
 * @see Table#removeRow(int)
 * @see Table#clearRows()
 * @see Table#getRow(int)
 * @see Table#rows()
 */
public interface TableRow {

  /**
   * Retrieves a String value from the <b>TableRow</b>'s specified column. 
   * The column may be specified by either its ID or title.
   *
   * @webref tablerow:method
   * @webBrief Get a <b>String</b> value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getFloat(int)
   */
  public String getString(int column);

  /**
   * @param columnName title of the column to reference
   */
  public String getString(String columnName);

  /**
   * Retrieves an integer value from the <b>TableRow</b>'s specified column. 
   * The column may be specified by either its ID or title.
   *
   * @webref tablerow:method
   * @webBrief Get an <b>integer</b> value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getFloat(int)
   * @see TableRow#getString(int)
   */
  public int getInt(int column);

  /**
   * @param columnName title of the column to reference
   */
  public int getInt(String columnName);

  /**
   * @webBrief Get a <b>long</b> value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getFloat(int)
   * @see TableRow#getString(int)
   */

  public long getLong(int column);

  /**
   * @param columnName title of the column to reference
   */
  public long getLong(String columnName);

  /**
   * Retrieves a float value from the <b>TableRow</b>'s specified column. 
   * The column may be specified by either its ID or title.
   *
   * @webref tablerow:method
   * @webBrief Get a <b>float</b> value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getString(int)
   */
  public float getFloat(int column);

  /**
   * @param columnName title of the column to reference
   */
  public float getFloat(String columnName);
  
  /**
   * @webBrief Get a <b>double</b> value from the specified column
   * @param column ID number of the column to reference
   * @see TableRow#getInt(int)
   * @see TableRow#getString(int)
   */
  public double getDouble(int column);
  
  /**
   * @param columnName title of the column to reference
   */
  public double getDouble(String columnName);

  /**
   * Stores a <b>String</b> value in the <b>TableRow</b>'s specified column. The column 
   * may be specified by either its ID or title.
   *
   * @webref tablerow:method
   * @webBrief Store a <b>String</b> value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setInt(int, int)
   * @see TableRow#setFloat(int, float)
   */
  public void setString(int column, String value);
  /**
   * @param columnName title of the target column
   */
  public void setString(String columnName, String value);

  /**
   * Stores an <b>integer</b> value in the <b>TableRow</b>'s specified column. The column 
   * may be specified by either its ID or title.
   * 
   * @webref tablerow:method
   * @webBrief Store an <b>integer</b> value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setInt(int column, int value);

  /**
   * @param columnName title of the target column
   */
  public void setInt(String columnName, int value);
  
  /**
   * @webBrief Store a <b>long</b> value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setLong(int column, long value);
  
  /**
   * @param columnName title of the target column
   */
  public void setLong(String columnName, long value);

  /**
   * Stores a <b>float</b> value in the <b>TableRow</b>'s specified column. The column 
   * may be specified by either its ID or title.
   *
   * @webref tablerow:method
   * @webBrief Store a <b>float</b> value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setInt(int, int)
   * @see TableRow#setString(int, String)
   */
  public void setFloat(int column, float value);
  
  /**
   * @param columnName title of the target column
   */
  public void setFloat(String columnName, float value);

  /**
   * @webBrief Store a <b>double</b> value in the specified column
   * @param column ID number of the target column
   * @param value value to assign
   * @see TableRow#setFloat(int, float)
   * @see TableRow#setString(int, String)
   */
  public void setDouble(int column, double value);
  
  /**
   * @param columnName title of the target column
   */
  public void setDouble(String columnName, double value);

  /**
   * Returns the number of columns in a <b>TableRow</b>.
   *
   * @webref tablerow:method
   * @webBrief Get the column count
   * @return count of all columns
   */
  public int getColumnCount();
  
  /**
   * @webBrief Get the column type
   * @param columnName title of the target column
   * @return type of the column
   */
  public int getColumnType(String columnName);
  
  /**
   * @param column ID number of the target column
   */
  public int getColumnType(int column);
  
  /**
   * @webBrief Get the all column types
   * @return list of all column types
   */
  public int[] getColumnTypes();

  /**
   * Returns the name for a column in a <b>TableRow</b> based on its ID (e.g. 0, 1, 2, etc.) 
   *
   * @webref tablerow:method
   * @webBrief Get the column title.
   * @param column ID number of the target column
   * @return title of the column
   */
  public String getColumnTitle(int column);

  /**
   * @webBrief Get the all column titles
   * @return list of all column titles
   */
  public String[] getColumnTitles();

  public void write(PrintWriter writer);
  public void print();
}
