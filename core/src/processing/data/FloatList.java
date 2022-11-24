package processing.data;

import java.io.File;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;

import processing.core.PApplet;


/**
 * Helper class for a list of <b>float</b> values. Lists are designed
 * to have some features of <b>ArrayList</b>, but to maintain the
 * simplicity and efficiency of working with arrays.
 * Functions such as <b>sort()</b> and <b>shuffle()</b> always act on
 * the list itself. To get a sorted copy, use <b>list.copy().sort()</b>.
 *
 * @webref data:composite
 * @webBrief Helper class for a list of floats
 * @see IntList
 * @see StringList
 */
public class FloatList implements Iterable<Float> {
  int count;
  float[] data;


  public FloatList() {
    data = new float[10];
  }


  /**
   * @nowebref
   */
  public FloatList(int length) {
    data = new float[length];
  }


  /**
   * @nowebref
   */
  public FloatList(float[] list) {
    count = list.length;
    data = new float[count];
    System.arraycopy(list, 0, data, 0, count);
  }


  /**
   * Construct an FloatList from an iterable pile of objects.
   * For instance, a float array, an array of strings, who knows.
   * Un-parsable or null values will be set to NaN.
   * @nowebref
   */
  public FloatList(Iterable<Object> iterator) {
    this(10);
    for (Object o : iterator) {
      if (o == null) {
        append(Float.NaN);
      } else if (o instanceof Number) {
        append(((Number) o).floatValue());
      } else {
        append(PApplet.parseFloat(o.toString().trim()));
      }
    }
    crop();
  }


  /**
   * Construct an FloatList from a random pile of objects.
   * Un-parsable or null values will be set to NaN.
   */
  public FloatList(Object... items) {
    // nuts, no good way to pass missingValue to this fn (varargs must be last)
    final float missingValue = Float.NaN;

    count = items.length;
    data = new float[count];
    int index = 0;
    for (Object o : items) {
      float value = missingValue;
      if (o != null) {
        if (o instanceof Number) {
          value = ((Number) o).floatValue();
        } else {
          value = PApplet.parseFloat(o.toString().trim(), missingValue);
        }
      }
      data[index++] = value;
    }
  }


  /**
   * Improve efficiency by removing allocated but unused entries from the
   * internal array used to store the data. Set to private, though it could
   * be useful to have this public if lists are frequently making drastic
   * size changes (from very large to very small).
   */
  private void crop() {
    if (count != data.length) {
      data = PApplet.subset(data, 0, count);
    }
  }


  /**
   * Get the length of the list.
   *
   * @webref floatlist:method
   * @webBrief Get the length of the list
   */
  public int size() {
    return count;
  }


  public void resize(int length) {
    if (length > data.length) {
      float[] temp = new float[length];
      System.arraycopy(data, 0, temp, 0, count);
      data = temp;

    } else if (length > count) {
      Arrays.fill(data, count, length, 0);
    }
    count = length;
  }


  /**
   * Remove all entries from the list.
   *
   * @webref floatlist:method
   * @webBrief Remove all entries from the list
   */
  public void clear() {
    count = 0;
  }


  /**
   * Get an entry at a particular index.
   *
   * @webref floatlist:method
   * @webBrief Get an entry at a particular index
   */
  public float get(int index) {
    if (index >= count) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    return data[index];
  }


  /**
   * Set the entry at a particular index. 
   *
   * @webref floatlist:method
   * @webBrief Set the entry at a particular index
   */
  public void set(int index, float what) {
    if (index >= count) {
      data = PApplet.expand(data, index+1);
      for (int i = count; i < index; i++) {
        data[i] = 0;
      }
      count = index+1;
    }
    data[index] = what;
  }


  /** Just an alias for append(), but matches pop() */
  public void push(float value) {
    append(value);
  }


  public float pop() {
    if (count == 0) {
      throw new RuntimeException("Can't call pop() on an empty list");
    }
    float value = get(count-1);
    count--;
    return value;
  }


  /**
   * Remove an element from the specified index.
   *
   * @webref floatlist:method
   * @webBrief Remove an element from the specified index
   */
  public float remove(int index) {
    if (index < 0 || index >= count) {
      throw new ArrayIndexOutOfBoundsException(index);
    }
    float entry = data[index];
//    int[] outgoing = new int[count - 1];
//    System.arraycopy(data, 0, outgoing, 0, index);
//    count--;
//    System.arraycopy(data, index + 1, outgoing, 0, count - index);
//    data = outgoing;
    // For most cases, this actually appears to be faster
    // than arraycopy() on an array copying into itself.
    for (int i = index; i < count-1; i++) {
      data[i] = data[i+1];
    }
    count--;
    return entry;
  }


  // Remove the first instance of a particular value,
  // and return the index at which it was found.
  @SuppressWarnings("unused")
  public int removeValue(int value) {
    int index = index(value);
    if (index != -1) {
      remove(index);
      return index;
    }
    return -1;
  }


  // Remove all instances of a particular value,
  // and return the number of values found and removed
  @SuppressWarnings("unused")
  public int removeValues(float value) {
    int ii = 0;
    if (Float.isNaN(value)) {
      for (int i = 0; i < count; i++) {
        if (!Float.isNaN(data[i])) {
          data[ii++] = data[i];
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (data[i] != value) {
          data[ii++] = data[i];
        }
      }
    }
    int removed = count - ii;
    count = ii;
    return removed;
  }


  /** Replace the first instance of a particular value */
  @SuppressWarnings("unused")
  public boolean replaceValue(float value, float newValue) {
    if (Float.isNaN(value)) {
      for (int i = 0; i < count; i++) {
        if (Float.isNaN(data[i])) {
          data[i] = newValue;
          return true;
        }
      }
    } else {
      int index = index(value);
      if (index != -1) {
        data[index] = newValue;
        return true;
      }
    }
    return false;
  }


  /** Replace all instances of a particular value */
  @SuppressWarnings("unused")
  public boolean replaceValues(float value, float newValue) {
    boolean changed = false;
    if (Float.isNaN(value)) {
      for (int i = 0; i < count; i++) {
        if (Float.isNaN(data[i])) {
          data[i] = newValue;
          changed = true;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (data[i] == value) {
          data[i] = newValue;
          changed = true;
        }
      }
    }
    return changed;
  }



  /**
   * Add a new entry to the list.
   *
   * @webref floatlist:method
   * @webBrief Add a new entry to the list
   */
  public void append(float value) {
    if (count == data.length) {
      data = PApplet.expand(data);
    }
    data[count++] = value;
  }


  public void append(float[] values) {
    for (float v : values) {
      append(v);
    }
  }


  public void append(FloatList list) {
    for (float v : list.values()) {  // will concat the list...
      append(v);
    }
  }


  /** Add this value, but only if it's not already in the list. */
  @SuppressWarnings("unused")
  public void appendUnique(float value) {
    if (!hasValue(value)) {
      append(value);
    }
  }


  public void insert(int index, float value) {
    insert(index, new float[] { value });
  }


  // same as splice
  public void insert(int index, float[] values) {
    if (index < 0) {
      throw new IllegalArgumentException("insert() index cannot be negative: it was " + index);
    }
    if (index >= data.length) {
      throw new IllegalArgumentException("insert() index " + index + " is past the end of this list");
    }

    float[] temp = new float[count + values.length];

    // Copy the old values, but not more than already exist
    System.arraycopy(data, 0, temp, 0, Math.min(count, index));

    // Copy the new values into the proper place
    System.arraycopy(values, 0, temp, index, values.length);

//    if (index < count) {
    // The index was inside count, so it's a true splice/insert
    System.arraycopy(data, index, temp, index+values.length, count - index);
    count = count + values.length;
//    } else {
//      // The index was past 'count', so the new count is weirder
//      count = index + values.length;
//    }
    data = temp;
  }


  public void insert(int index, FloatList list) {
    insert(index, list.values());
  }


  /** Return the first index of a particular value. */
  public int index(float what) {
    for (int i = 0; i < count; i++) {
      if (data[i] == what) {
        return i;
      }
    }
    return -1;
  }


  /**
   * Check if a number is a part of the list.
   *
   * @webref floatlist:method
   * @webBrief Check if a number is a part of the list
   */
  public boolean hasValue(float value) {
    if (Float.isNaN(value)) {
      for (int i = 0; i < count; i++) {
        if (Float.isNaN(data[i])) {
          return true;
        }
      }
    } else {
      for (int i = 0; i < count; i++) {
        if (data[i] == value) {
          return true;
        }
      }
    }
    return false;
  }


  private void boundsProblem(int index, String method) {
    final String msg = String.format("The list size is %d. " +
      "You cannot %s() to element %d.", count, method, index);
    throw new ArrayIndexOutOfBoundsException(msg);
  }


  /**
   * Add to a value.
   * 
   * @webref floatlist:method
   * @webBrief Add to a value
   */
  public void add(int index, float amount) {
    if (index < count) {
      data[index] += amount;
    } else {
      boundsProblem(index, "add");
    }
  }


  /**
   * Subtract from a value
   *
   * @webref floatlist:method
   * @webBrief Subtract from a value
   */
  public void sub(int index, float amount) {
    if (index < count) {
      data[index] -= amount;
    } else {
      boundsProblem(index, "sub");
    }
  }


  /**
   * Multiply a value
   * 
   * @webref floatlist:method
   * @webBrief Multiply a value
   */
  public void mult(int index, float amount) {
    if (index < count) {
      data[index] *= amount;
    } else {
      boundsProblem(index, "mult");
    }
  }


  /**
   * Divide a value
   *
   * @webref floatlist:method
   * @webBrief Divide a value
   */
  public void div(int index, float amount) {
    if (index < count) {
      data[index] /= amount;
    } else {
      boundsProblem(index, "div");
    }
  }


  private void checkMinMax(String functionName) {
    if (count == 0) {
      String msg =
        String.format("Cannot use %s() on an empty %s.",
                      functionName, getClass().getSimpleName());
      throw new RuntimeException(msg);
    }
  }


  /**
   * Return the smallest value
   *
   * @webref floatlist:method
   * @webBrief  Return the smallest value
   */
  public float min() {
    checkMinMax("min");
    int index = minIndex();
    return index == -1 ? Float.NaN : data[index];
  }


  public int minIndex() {
    checkMinMax("minIndex");
    float m;
    int mi = -1;
    for (int i = 0; i < count; i++) {
      // find one good value to start
      if (data[i] == data[i]) {
        m = data[i];
        mi = i;

        // calculate the rest
        for (int j = i+1; j < count; j++) {
          float d = data[j];
          if (!Float.isNaN(d) && (d < m)) {
            m = data[j];
            mi = j;
          }
        }
        break;
      }
    }
    return mi;
  }


  /**
   * Return the largest value
   *
   * @webref floatlist:method
   * @webBrief Return the largest value
   */
  public float max() {
    checkMinMax("max");
    int index = maxIndex();
    return index == -1 ? Float.NaN : data[index];
  }


  public int maxIndex() {
    checkMinMax("maxIndex");
    float m;
    int mi = -1;
    for (int i = 0; i < count; i++) {
      // find one good value to start
      if (data[i] == data[i]) {
        m = data[i];
        mi = i;

        // calculate the rest
        for (int j = i+1; j < count; j++) {
          float d = data[j];
          if (!Float.isNaN(d) && (d > m)) {
            m = data[j];
            mi = j;
          }
        }
        break;
      }
    }
    return mi;
  }


  public float sum() {
    double amount = sumDouble();
    if (amount > Float.MAX_VALUE) {
      throw new RuntimeException("sum() exceeds " + Float.MAX_VALUE + ", use sumDouble()");
    }
    if (amount < -Float.MAX_VALUE) {
      throw new RuntimeException("sum() lower than " + -Float.MAX_VALUE + ", use sumDouble()");
    }
    return (float) amount;
  }


  public double sumDouble() {
    double sum = 0;
    for (int i = 0; i < count; i++) {
      sum += data[i];
    }
    return sum;
  }


  /**
   * Sorts an array, lowest to highest
   *
   * @webref floatlist:method
   * @webBrief Sorts an array, lowest to highest
   */
  public void sort() {
    Arrays.sort(data, 0, count);
  }


  /**
   * A sort in reverse. It's equivalent to running <b>sort()</b> and then 
   * <b>reverse()</b>, but is more efficient than running each separately.
   *
   * @webref floatlist:method
   * @webBrief A sort in reverse
   */
  public void sortReverse() {
    new Sort() {
      @Override
      public int size() {
        // if empty, don't even mess with the NaN check, it'll AIOOBE
        if (count == 0) {
          return 0;
        }
        // move NaN values to the end of the list and don't sort them
        int right = count - 1;
        while (data[right] != data[right]) {
          right--;
          if (right == -1) {  // all values are NaN
            return 0;
          }
        }
        for (int i = right; i >= 0; --i) {
          float v = data[i];
          if (v != v) {
            data[i] = data[right];
            data[right] = v;
            --right;
          }
        }
        return right + 1;
      }

      @Override
      public int compare(int a, int b) {
        float diff = data[b] - data[a];
        return diff == 0 ? 0 : (diff < 0 ? -1 : 1);
      }

      @Override
      public void swap(int a, int b) {
        float temp = data[a];
        data[a] = data[b];
        data[b] = temp;
      }
    }.run();
  }


  /**
   * Reverse the order of the list
   * 
   * @webref floatlist:method
   * @webBrief Reverse the order of the list
   */
  public void reverse() {
    int ii = count - 1;
    for (int i = 0; i < count/2; i++) {
      float t = data[i];
      data[i] = data[ii];
      data[ii] = t;
      --ii;
    }
  }


  /**
   * Randomize the order of the list elements.
   *
   * @webref floatlist:method
   * @webBrief Randomize the order of the list elements
   */
  @SuppressWarnings("unused")
  public void shuffle() {
    Random r = new Random();
    int num = count;
    while (num > 1) {
      int value = r.nextInt(num);
      num--;
      float temp = data[num];
      data[num] = data[value];
      data[value] = temp;
    }
  }


  /**
   * Randomize the list order using the random() function from the specified
   * sketch, allowing shuffle() to use its current randomSeed() setting.
   */
  @SuppressWarnings("unused")
  public void shuffle(PApplet sketch) {
    int num = count;
    while (num > 1) {
      int value = (int) sketch.random(num);
      num--;
      float temp = data[num];
      data[num] = data[value];
      data[value] = temp;
    }
  }


  /**
   * Return a random value from the list.
   */
  public float random() {
    if (count == 0) {
      throw new ArrayIndexOutOfBoundsException("No entries in this FloatList");
    }
    return data[(int) (Math.random() * count)];
  }


//  /**
//   * Return a random value from the list, using the
//   * randomSeed() from the specified sketch object.
//   */
//  public float random(PApplet sketch) {
//    if (count == 0) {
//      throw new ArrayIndexOutOfBoundsException("No entries in this FloatList");
//    }
//    return data[(int) sketch.random(count)];
//  }


  public float removeChoice() {
    if (count == 0) {
      throw new ArrayIndexOutOfBoundsException("No entries in this IntList");
    }
    int index = (int) (Math.random() * count);
    return remove(index);
  }


  public FloatList copy() {
    FloatList outgoing = new FloatList(data);
    outgoing.count = count;
    return outgoing;
  }


  /**
   * Returns the actual array being used to store the data. For advanced users,
   * this is the fastest way to access a large list. Suitable for iterating
   * with a for() loop, but modifying the list will have terrible consequences.
   */
  public float[] values() {
    crop();
    return data;
  }


  /** Implemented this way so that we can use a FloatList in a for loop. */
  @Override
  public Iterator<Float> iterator() {
    return new Iterator<>() {
      int index = -1;

      public void remove() {
        FloatList.this.remove(index);
        index--;
      }

      public Float next() {
        return data[++index];
      }

      public boolean hasNext() {
        return index+1 < count;
      }
    };
  }


  @Deprecated
  public float[] array() {
    return toArray();
  }


  /**
   * Create a new array with a copy of all the values.
   * @return an array sized by the length of the list with each of the values.
   * @webref floatlist:method
   * @webBrief Create a new array with a copy of all the values
   */
  public float[] toArray() {
    return toArray(null);
  }


  @Deprecated
  public float[] array(float[] array) {
    return toArray(array);
  }


  /**
   * Copy values into the specified array. If the specified array is
   * null or not the same size, a new array will be allocated.
   */
  public float[] toArray(float[] array) {
    if (array == null || array.length != count) {
      array = new float[count];
    }
    System.arraycopy(data, 0, array, 0, count);
    return array;
  }


  /**
   * Returns a normalized version of this array. Called getPercent()
   * for consistency with the Dict classes. It's a getter method
   * because it needs to return a new list (because IntList/Dict
   * can't do percentages or normalization in place on int values).
   */
  @SuppressWarnings("unused")
  public FloatList getPercent() {
    double sum = 0;
    for (float value : array()) {
      sum += value;
    }
    FloatList outgoing = new FloatList(count);
    for (int i = 0; i < count; i++) {
      double percent = data[i] / sum;
      outgoing.set(i, (float) percent);
    }
    return outgoing;
  }


  @SuppressWarnings("unused")
  public FloatList getSubset(int start) {
    return getSubset(start, count - start);
  }


  public FloatList getSubset(int start, int num) {
    float[] subset = new float[num];
    System.arraycopy(data, start, subset, 0, num);
    return new FloatList(subset);
  }


  public String join(String separator) {
    if (count == 0) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(data[0]);
    for (int i = 1; i < count; i++) {
      sb.append(separator);
      sb.append(data[i]);
    }
    return sb.toString();
  }


  public void print() {
    for (int i = 0; i < count; i++) {
      System.out.format("[%d] %f%n", i, data[i]);
    }
  }


  /**
   * Save tab-delimited entries to a file (TSV format, UTF-8 encoding)
   */
  public void save(File file) {
    PrintWriter writer = PApplet.createWriter(file);
    write(writer);
    writer.close();
  }


  /**
   * Write entries to a PrintWriter, one per line
   */
  public void write(PrintWriter writer) {
    for (int i = 0; i < count; i++) {
      writer.println(data[i]);
    }
    writer.flush();
  }


  /**
   * Return this dictionary as a String in JSON format.
   */
  public String toJSON() {
    return "[ " + join(", ") + " ]";
  }


  @Override
  public String toString() {
    return getClass().getSimpleName() + " size=" + size() + " " + toJSON();
  }
}
