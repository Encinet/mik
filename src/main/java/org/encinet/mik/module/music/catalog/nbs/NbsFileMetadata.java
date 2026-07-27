package org.encinet.mik.module.music.catalog.nbs;

/** Header fields retained for NBS inspection even though playback does not use them. */
public record NbsFileMetadata(
        int vanillaInstrumentCount,
        int declaredLengthTicks,
        int autoSaveFlag,
        int autoSaveIntervalMinutes,
        int timeSignature,
        int minutesSpent,
        int leftClicks,
        int rightClicks,
        int notesAdded,
        int notesRemoved,
        String importedFileName
) {
    public NbsFileMetadata {
        if (vanillaInstrumentCount < 1 || vanillaInstrumentCount > 255) {
            throw new IllegalArgumentException("vanillaInstrumentCount is out of range");
        }
        if (declaredLengthTicks < 0 || declaredLengthTicks > 65_535) {
            throw new IllegalArgumentException("declaredLengthTicks is out of range");
        }
        requireByte(autoSaveFlag, "autoSaveFlag");
        requireByte(autoSaveIntervalMinutes, "autoSaveIntervalMinutes");
        requireByte(timeSignature, "timeSignature");
        importedFileName = importedFileName == null ? "" : importedFileName;
    }

    public boolean autoSaveEnabled() {
        return autoSaveFlag != 0;
    }

    static NbsFileMetadata defaults(int version, int lengthTicks) {
        int declaredLength = version == 0 || version >= 3
                ? Math.min(65_535, Math.max(0, lengthTicks)) : 0;
        return new NbsFileMetadata(version >= 6 ? 20 : version == 0 ? 10 : 16,
                declaredLength,
                0, 10, 4, 0, 0, 0, 0, 0, "");
    }

    private static void requireByte(int value, String field) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(field + " is out of range");
        }
    }
}
