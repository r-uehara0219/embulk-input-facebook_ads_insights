package org.embulk.input.facebook_ads_insights;

import com.facebook.ads.sdk.APIException;
import com.facebook.ads.sdk.AdsInsights;
import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.exec.ExecutionInterruptedException;
import org.embulk.spi.Exec;
import org.embulk.spi.InputPlugin;
import org.embulk.spi.PageBuilder;
import org.embulk.spi.PageOutput;
import org.embulk.spi.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FacebookAdsInsightsInputPlugin
        implements InputPlugin
{
    private final Logger logger =  LoggerFactory.getLogger(FacebookAdsInsightsInputPlugin.class);

    @Override
    public ConfigDiff transaction(ConfigSource config,
            InputPlugin.Control control)
    {
        PluginTask task = config.loadConfig(PluginTask.class);
        Schema schema = task.getFields().toSchema();
        return resume(task.dump(), schema, 1, control);
    }

    @Override
    public ConfigDiff resume(TaskSource taskSource,
            Schema schema, int taskCount,
            InputPlugin.Control control)
    {
        control.run(taskSource, schema, taskCount);
        return Exec.newConfigDiff();
    }

    @Override
    public void cleanup(TaskSource taskSource,
            Schema schema, int taskCount,
            List<TaskReport> successTaskReports)
    {
    }

    @Override
    public TaskReport run(TaskSource taskSource,
            Schema schema, int taskIndex,
            PageOutput output)
    {
        PluginTask task = taskSource.loadTask(PluginTask.class);
        try {
            try (PageBuilder pageBuilder = new PageBuilder(Exec.getBufferAllocator(), schema, output)) {
                Client client = new Client(task);
                List<AdsInsights> insights = client.getInsights(!Exec.isPreview());
                for (AdsInsights insight : insights) {
                    schema.visitColumns(new FacebookAdsInsightsColumnVisitor(insight, pageBuilder, task));
                    pageBuilder.addRecord();
                }
                pageBuilder.finish();
            }
        }
        catch (APIException | InterruptedException e) {
            logger.error(e.getMessage(), e);
            throw new ExecutionInterruptedException(e);
        }
        return Exec.newTaskReport();
    }

    @Override
    public ConfigDiff guess(ConfigSource config)
    {
        return Exec.newConfigDiff();
    }
}
