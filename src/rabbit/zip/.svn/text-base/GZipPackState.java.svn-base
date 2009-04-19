package rabbit.zip;

/** The state a gzip packing can be in.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
interface GZipPackState {
    /** Check if the packer currently needs more data 
     */
    boolean needsInput ();

    /** Handle a buffer. 
     * @param buf the data to be handled.
     * @param off the start offset of the data.
     * @param len the length of the data.
     * @return the new State of gzip unpacking.
     */
    void handleBuffer (GZipPacker packer, byte[] buf, int off, int len);

    /** Handle the next block of the current data. 
     */
    void handleCurrentData (GZipPacker packer);

    /** Tell the current state that packing is finished.
     */
    void finish ();

    /** Check if packing is finished. */
    boolean finished ();
}
