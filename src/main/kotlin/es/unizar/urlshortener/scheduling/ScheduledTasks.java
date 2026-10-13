package es.unizar.urlshortener.scheduling;

import org.springframework.scheduling.annotation.Scheduled;

public class ScheduledTasks {


    @Scheduled(fixedRate = 60000)   //se ejecuta cada 60s=1min
	public void reportCurrentTime() {

	}

}
