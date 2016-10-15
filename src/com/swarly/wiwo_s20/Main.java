package com.swarly.wiwo_s20;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {


	public static void main(String[] args) throws InterruptedException {
		WsSocketListner wsSocketListner = new WsSocketListner();
		wsSocketListner.start();
		ExecutorService service = Executors.newSingleThreadExecutor();
		service.submit(wsSocketListner.getListeners().get(0).sendToggle());
		Thread.sleep(1000);
	}
}
