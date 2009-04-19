package rabbit.zip;

/** The state a gzip unpacking can be in.
 *
 * @author <a href="mailto:robo@khelekore.org">Robert Olofsson</a>
 */
interface GZipUnpackState {
    /** Check if the unpacker currently needs more data 
     */
    boolean needsInput ();

    /** Handle a buffer. 
     * @param buf the data to be handled.
     * @param off the start offset of the data.
     * @param len the length of the data.
     * @return the new State of gzip unpacking.
     */
    void handleBuffer (GZipUnpacker unpacker, byte[] buf, int off, int len);

    /** Handle the next block of the current data. 
     */
    void handleCurrentData (GZipUnpacker unpacker);
}
