package org.dstadler.commoncrawl.oldindex;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.commoncrawl.hadoop.mapred.ArcRecord;
import org.dstadler.commoncrawl.Utils;
import org.dstadler.commons.http.HttpClientWrapper;
import org.dstadler.commons.logging.jdk.LoggerFactory;

/**
 * Specialized Processor which reads the position in the Common Crawl
 * from the URL in the Block and then downloads and unwraps the actual
 * document in one go.
 *
 * @author dominik.stadler
 */
public class ProcessAndDownload extends ProcessImpl implements Closeable {
    private final static Logger log = LoggerFactory.make();
    
    private final HttpClientWrapper client = new HttpClientWrapper("", null, 30_000); 

    public ProcessAndDownload(File file, boolean append) throws IOException {
        super(file, append);
    }

    @Override
    protected void handle(String url, byte[] block, int headerStart, long blockIndex) throws IOException {
        // read location information
        BlockHeader header = BlockHeader.read(block, headerStart);

        // check if we already have that file
        File destFile = Utils.computeDownloadFileName(url, "");
        if(destFile.exists()) {
            log.info(blockIndex + ": File " + destFile + " already downloaded");
            return;
        }

        log.info(blockIndex + ": Reading file for " + url + " at " + header.getRangeHader() + " from " + header.getUrl() + " to " + destFile);

        // do a range-query for exactly this document in the Common Crawl dataset
        HttpGet httpGet = new HttpGet(header.getUrl());
        httpGet.addHeader("Range", header.getRangeHader());
        try (CloseableHttpResponse response = client.getHttpClient().execute(httpGet)) {
            HttpEntity entity = Utils.checkAndFetch(response, header.getUrl());
            
            ArcRecord record = new ArcRecord();
            try (InputStream stream = new GZIPInputStream(entity.getContent())) {
            	// could not use WARCRecord or ARCRecord here as it expects a different format?!?
            	// try (WARCRecord record = new WARCRecord(stream, destFile.getName(), 0)) {
            	//    FileUtils.copyInputStreamToFile(record, destFile);

            	record.readFrom(stream);
                try {
                	FileUtils.copyInputStreamToFile(record.getHttpResponse().getEntity().getContent(), destFile);
                } catch (IllegalStateException  | HttpException e) {
                	throw new IOException(e);
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        super.close();

        client.close();
    }
}