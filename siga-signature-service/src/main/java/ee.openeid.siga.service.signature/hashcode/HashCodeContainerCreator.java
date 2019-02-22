package ee.openeid.siga.service.signature.hashcode;

import ee.openeid.siga.common.HashCodeDataFile;
import ee.openeid.siga.common.exception.TechnicalException;
import eu.europa.esig.dss.MimeType;
import org.digidoc4j.Container;
import org.digidoc4j.DigestAlgorithm;
import org.digidoc4j.Signature;
import org.digidoc4j.impl.asic.manifest.AsicManifest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class HashCodeContainerCreator {

    private final ZipOutputStream zipOutputStream;
    private static final String ZIP_ENTRY_MIMETYPE = "mimetype";
    public static final String SIGNATURE_FILE_PREFIX = "META-INF/signatures";
    private static final String SIGNATURE_FILE_EXTENSION = ".xml";


    public HashCodeContainerCreator(OutputStream outputStream) {
        this.zipOutputStream = new ZipOutputStream(outputStream, Charset.forName("UTF-8"));
    }

    public void writeHashCodeFiles(List<HashCodeDataFile> dataFiles) {
        writeHashCodeFile(dataFiles, DigestAlgorithm.SHA256, HashCodesDataFile.HASHCODES_SHA256);
        writeHashCodeFile(dataFiles, DigestAlgorithm.SHA512, HashCodesDataFile.HASHCODES_SHA512);
    }

    private void writeHashCodeFile(List<HashCodeDataFile> dataFiles, DigestAlgorithm digestAlgorithm, String entryName) {
        HashCodesDataFile hashCodesDataFile = new HashCodesDataFile(digestAlgorithm);
        hashCodesDataFile.generateHashCodeFile(dataFiles);
        new EntryCallback(new ZipEntry(entryName)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                hashCodesDataFile.writeTo(stream);
            }
        }.write();
    }

    public void finalizeZipFile() {
        try {
            zipOutputStream.finish();
            zipOutputStream.close();
        } catch (IOException e) {
            throw new TechnicalException("Unable to finish creating ZIP container");
        }
    }

    public void writeMimeType() {
        byte[] mimeType = MimeType.ASICE.getMimeTypeString().getBytes();
        new BytesEntryCallback(getZipEntry(mimeType, ZIP_ENTRY_MIMETYPE), mimeType).write();
    }

    public void writeSignatures(List<Signature> signatures) {
        for (int i = 0; i < signatures.size(); i++) {
            byte[] signatureData = signatures.get(0).getAdESSignature();
            String signatureName = SIGNATURE_FILE_PREFIX + i + SIGNATURE_FILE_EXTENSION;
            new BytesEntryCallback(getZipEntry(signatureData, signatureName), signatureData).write();
        }
    }

    private static ZipEntry getZipEntry(byte[] mimeTypeBytes, String name) {
        ZipEntry zipEntry = new ZipEntry(name);
        zipEntry.setMethod(ZipEntry.STORED);
        zipEntry.setSize(mimeTypeBytes.length);
        zipEntry.setCompressedSize(mimeTypeBytes.length);
        CRC32 crc = new CRC32();
        crc.update(mimeTypeBytes);
        zipEntry.setCrc(crc.getValue());
        return zipEntry;
    }

    public void writeManifest(List<org.digidoc4j.DataFile> dataFiles) {
        final AsicManifest asicManifest = new AsicManifest(Container.DocumentType.ASICE.name());
        asicManifest.addFileEntry(dataFiles);
        new EntryCallback(new ZipEntry(AsicManifest.XML_PATH)) {
            @Override
            void doWithEntryStream(OutputStream stream) {
                asicManifest.writeTo(stream);
            }
        }.write();
    }

    private class BytesEntryCallback extends EntryCallback {

        private final byte[] data;

        BytesEntryCallback(ZipEntry entry, byte[] data) {
            super(entry);
            this.data = data;
        }

        @Override
        void doWithEntryStream(OutputStream stream) throws IOException {
            stream.write(data);
        }
    }

    private abstract class EntryCallback {

        private final ZipEntry entry;

        EntryCallback(ZipEntry entry) {
            this.entry = entry;
        }

        void write() {

            try {
                zipOutputStream.putNextEntry(entry);
                doWithEntryStream(zipOutputStream);
                zipOutputStream.closeEntry();
            } catch (IOException e) {
                throw new TechnicalException("Unable to write Zip entry");
            }
        }

        abstract void doWithEntryStream(OutputStream stream) throws IOException;
    }

}
