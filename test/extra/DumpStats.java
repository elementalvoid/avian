package extra;

import java.io.PrintStream;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.EOFException;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;
import java.util.Arrays;

/**
 * This is a simple utility to generate and print statistics from a
 * heap dump generated by Avian's heapdump.cpp.  The output is a list
 * of classes (identified by number in the case of anonymous,
 * VM-internal classes), each followed by (1) the total memory
 * footprint of all instances of the class in machine words, and (2)
 * the number of instances.  The output is ordered by instance memory
 * footprint.
 */
public class DumpStats {
  private static final int Root = 0;
  private static final int Size = 1;
  private static final int ClassName = 2;
  private static final int Push = 3;
  private static final int Pop = 4;

  private static int readInt(InputStream in) throws IOException {
    int b1 = in.read();
    int b2 = in.read();
    int b3 = in.read();
    int b4 = in.read();
    if (b4 == -1) throw new EOFException();
    return (int) ((b1 << 24) | (b2 << 16) | (b3 << 8) | (b4));    
  }

  private static String readString(InputStream in) throws IOException {
    int count = readInt(in);
    byte[] b = new byte[count];
    int offset = 0;
    int c;
    while ((c = in.read(b, offset, b.length - offset)) != -1
           && offset < b.length)
    {
      offset += c;
    }
    if (offset != b.length) throw new EOFException();
    return new String(b);
  }

  private static Record record(Map<Integer, Record> map, int key) {
    Record r = map.get(key);
    if (r == null) {
      map.put(key, r = new Record(key));
    }
    return r;
  }

  private static Map<Integer, Record> read(InputStream in)
    throws IOException
  {
    boolean done = false;
    boolean popped = false;
    int size = 0;
    int last = 0;
    Map<Integer, Record> map = new HashMap();

    while (! done) {
      int flag = in.read();
      switch (flag) {
      case Root: {
        last = readInt(in);
        popped = false;
      } break;

      case ClassName: {
        record(map, last).name = readString(in);
      } break;

      case Push: {
        last = readInt(in);
        if (! popped) {
          Record r = record(map, last);
          r.footprint += size;
          ++ r.count;
        }
        popped = false;
      } break;

      case Pop: {
        popped = true;
      } break;

      case Size: {
        size = readInt(in);
      } break;

      case -1:
        done = true;
        break;

      default:
        throw new RuntimeException("bad flag: " + flag);
      }
    }

    return map;
  }

  private static void usageAndExit() {
    System.err.println("usage: java DumpStats <heap dump> <word size>");
  }
  
  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      usageAndExit();
    }

    Map<Integer, Record> map = read
      (new BufferedInputStream(new FileInputStream(args[0])));

    Record[] array = map.values().toArray(new Record[map.size()]);
    Arrays.sort(array, new Comparator<Record>() {
        public int compare(Record a, Record b) {
          return b.footprint - a.footprint;
        }
      });

    int wordSize = Integer.parseInt(args[1]);

    int footprint = 0;
    int count = 0;
    for (Record r: array) {
      if (r.name == null) {
        r.name = String.valueOf(r.key);
      }
      System.out.println
        (r.name + ": " + (r.footprint * wordSize) + " " + r.count);
      footprint += r.footprint;
      count += r.count;
    }

    System.out.println();
    System.out.println("total: " + (footprint * wordSize) + " " + count);
  }

  private static class Record {
    public final int key;
    public String name;
    public int footprint;
    public int count;

    public Record(int key) {
      this.key = key;
    }
  }
}