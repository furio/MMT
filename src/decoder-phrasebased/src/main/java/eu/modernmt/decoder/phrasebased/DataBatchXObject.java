package eu.modernmt.decoder.phrasebased;

import eu.modernmt.data.DataBatch;
import eu.modernmt.data.Deletion;
import eu.modernmt.data.TranslationUnit;
import eu.modernmt.lang.LanguagePair;

import java.util.Map;

/**
 * Created by davide on 31/10/17.
 */
class DataBatchXObject {

    public final short[] tuChannels;
    public final long[] tuChannelPositions;
    public final long[] tuMemories;
    public final String[] tuSources;
    public final String[] tuTargets;
    public final int[][] tuAlignments;

    public final short[] delChannels;
    public final long[] delChannelPositions;
    public final long[] delMemories;

    public final short[] channels;
    public final long[] channelPositions;

    public DataBatchXObject(DataBatch batch, LanguagePair direction) {
        int i;

        // Translation units -------------------------------------------------------------------------------------------
        int tuSize = 0;
        for (TranslationUnit unit : batch.getTranslationUnits()) {
            if (direction.equals(unit.direction))
                tuSize++;
        }

        tuChannels = new short[tuSize];
        tuChannelPositions = new long[tuSize];
        tuMemories = new long[tuSize];
        tuSources = new String[tuSize];
        tuTargets = new String[tuSize];
        tuAlignments = new int[tuSize][];

        i = 0;
        for (TranslationUnit unit : batch.getTranslationUnits()) {
            if (!direction.equals(unit.direction))
                continue;

            tuChannels[i] = unit.channel;
            tuChannelPositions[i] = unit.channelPosition;
            tuMemories[i] = unit.memory;
            tuSources[i] = XUtils.encodeSentence(unit.sentence);
            tuTargets[i] = XUtils.encodeSentence(unit.translation);
            tuAlignments[i] = XUtils.encodeAlignment(unit.alignment);

            i++;
        }

        // Deletions ---------------------------------------------------------------------------------------------------
        int delSize = batch.getDeletions().size();

        delChannels = new short[delSize];
        delChannelPositions = new long[delSize];
        delMemories = new long[delSize];

        i = 0;
        for (Deletion deletion : batch.getDeletions()) {
            delChannels[i] = deletion.channel;
            delChannelPositions[i] = deletion.channelPosition;
            delMemories[i] = deletion.memory;

            i++;
        }

        // Channel positions -------------------------------------------------------------------------------------------
        int size = batch.getChannelPositions().size();

        channels = new short[size];
        channelPositions = new long[size];

        i = 0;
        for (Map.Entry<Short, Long> entry : batch.getChannelPositions().entrySet()) {
            channels[i] = entry.getKey();
            channelPositions[i] = entry.getValue();

            i++;
        }
    }
}
