package slack

import javax.inject.Inject

import com.google.inject.Injector
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.quartz._
import org.quartz.spi.JobFactory
import org.quartz.spi.TriggerFiredBundle
import play.Configuration
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

class GuiceJobFactory @Inject()(guice: Injector) extends JobFactory {

  @throws(classOf[SchedulerException])
  override def newJob(bundle: TriggerFiredBundle, scheduler: Scheduler): Job = {
    guice.getInstance(bundle.getJobDetail().getJobClass)
  }
}

@throws(classOf[SchedulerException])
class SlackScheduler @Inject()(factory: SchedulerFactory,
                               jobFactory: GuiceJobFactory,
                               config: Configuration,
                               lifecycle: ApplicationLifecycle)
  extends StrictLogging {
  val scheduler = factory.getScheduler
  scheduler.setJobFactory(jobFactory)

  val slackJob = JobBuilder.newJob().withIdentity("slack-job").ofType(classOf[SlackJob]).build()
  val slackTrigger = TriggerBuilder
    .newTrigger()
    .withIdentity("slack-trigger-name").withSchedule(
    CronScheduleBuilder.cronSchedule(config.getString("slack.schedule"))).build()

  // Schedule the job
  scheduler.scheduleJob(slackJob, slackTrigger)

  // Start
  logger.info("Starting the scheduler")
  scheduler.start()

  // Shutdown on interrupt
  lifecycle.addStopHook { () =>
    Future.successful {
      logger.info("Shutting down scheduler")
      scheduler.shutdown()
    }
  }
}