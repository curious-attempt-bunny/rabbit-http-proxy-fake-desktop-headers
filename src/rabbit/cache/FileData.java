package rabbit.cache;

import java.io.Serializable;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** A class to store cache data to a file.
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
class FileData<T> implements Serializable {
    private static final long serialVersionUID = 1;
    private long fileSize;

    public long getFileSize () {
	return fileSize;
    }
    
    /** Read the data from disk. 
     */
    protected T readData (String name, FileHandler<T> fh) {
	InputStream is = null;
	try {
	    File f = new File (name);
	    if (!f.exists())
		return null;
	    FileInputStream fis = new FileInputStream (f);
	    is = new GZIPInputStream (fis);
	    return fh.read (is);
	} catch (IOException e) {
	    e.printStackTrace ();
	} finally {
	    closeIt (is);
	}
	return null;
    }

    protected long writeData (String name, FileHandler<T> fh, T data) {
	OutputStream os = null;
	File f = new File (name);
	try {
	    FileOutputStream fos = new FileOutputStream (f);
	    os = new GZIPOutputStream (fos);
	    fh.write (os, data);
	} catch (IOException e) {
	    e.printStackTrace ();
	    return 0;
	} finally {
	    closeIt (os);
	}
	fileSize = f.length ();
	return fileSize;
    }
    
    private void closeIt (Closeable c) {
	if (c != null) {
	    try {
		c.close ();
	    } catch (IOException e) {
		e.printStackTrace ();
	    }
	}
    }    
}
