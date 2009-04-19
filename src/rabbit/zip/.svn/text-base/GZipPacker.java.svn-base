package rabbit.zip;

/** A class that can pack gzip streams in chunked mode.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
public class GZipPacker {
    private GZipPackState state;
    
    /** Create a gzip packer that sends events to the given listener. 
     */
    public GZipPacker (GZipPackListener listener) {
	state = new HeaderWriter (listener);
    }

    /** Check if the unpacker currently needs more data 
     */
    public boolean needsInput () {
	return state.needsInput ();
    }

    /** Add more compressed data to the unpacker.
     */
    public void setInput (byte[] buf, int off, int len) {
	state.handleBuffer (this, buf, off, len);
    }

    /** Tell the packer that it has reached the end of data.
     */ 
    public void finish () {
	state.finish ();
    }

    /** Check if the packer is finished.
     */ 
    public boolean finished () {
	return state.finished ();
    }

    /** Handle the next block of the current data. 
     */
    public void handleCurrentData () {
	state.handleCurrentData (this);
    }

    /** Change the internal gzip state to the given state.
     * @param state the new internal state of the gzip packer.
     */ 
    public void setState (GZipPackState state) {
	this.state = state;
    }
}
