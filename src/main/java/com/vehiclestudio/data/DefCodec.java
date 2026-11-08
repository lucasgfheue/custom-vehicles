package com.vehiclestudio.data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/** Empaqueta definiciones como JSON comprimido para enviarlas por la red. */
public final class DefCodec {
    /** Limite de seguridad al descomprimir (evita "bombas zip"). */
    private static final int MAX_JSON_BYTES = 8 * 1024 * 1024;

    private DefCodec() {
    }

    public static byte[] encode(VehicleDefinition d) {
        byte[] raw = VehicleJson.write(d, false).getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(raw.length / 4 + 64);
        try (DeflaterOutputStream out = new DeflaterOutputStream(bos, new Deflater(Deflater.BEST_COMPRESSION))) {
            out.write(raw);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return bos.toByteArray();
    }

    public static VehicleDefinition decode(byte[] data) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(data.length * 4);
            byte[] buf = new byte[8192];
            int total = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                total += r;
                if (total > MAX_JSON_BYTES) {
                    throw new IOException("Definicion demasiado grande");
                }
                bos.write(buf, 0, r);
            }
            return VehicleJson.read(bos.toString(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IOException("Definicion invalida: " + e.getMessage(), e);
        }
    }
}
