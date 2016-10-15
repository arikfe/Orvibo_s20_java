package com.swarly.wiwo_s20;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;

import com.swarly.wiwo_s20.WsSocket.Request;

public class WsSocketListner 
{
	protected static final int PORT = 10000;
	private List<WsSocket> listeners;
	private Thread listenThread;
	private ExecutorService executor;

	public WsSocketListner()
	{
		super();
		listeners = new ArrayList<>();
		this.executor = Executors.newSingleThreadExecutor();
	}

	public void stop()
	{
		listenThread.interrupt();
	}

	@PostConstruct
	public void start()
	{
		listenThread = new Thread(() ->
		{
			try (DatagramSocket listen = new DatagramSocket(PORT)) {
				while (true) {
					byte[] res = new byte[128];
					DatagramPacket p = new DatagramPacket(res, 128);

					try {
						listen.receive(p);
						Optional<Request> request = Arrays.stream(Request.values())
								.filter(r -> r.isResponsePacket(p.getData())).findFirst();
						if (request.isPresent()) {
							System.out.println(request.get().name() + " was captured ");
							Request request2 = request.get();
							if (Request.Control == request2)
								listeners.stream()
								.filter(ws1 -> Arrays.equals(ws1.getMacAddress(),
										request2.getMacAddress(p.getData())))
								.forEach(ws2 -> ws2.update(p.getData()));
							if (Request.Discovery == request2) {
								WsSocket socket = new WsSocket();
								socket.setSocketListener(WsSocketListner.this);
								socket.setAddress(p.getAddress().getHostAddress());
								socket.setMacAddress(Request.Discovery.getMacAddress(p.getData()));
								if (!listeners.stream()
										.anyMatch(ws3 -> ws3.getAddress()
												.equals(p.getAddress()
														.getHostAddress())))
								{
									listeners.add(socket);
								}
								
							}
							if (request2 == Request.Unknown)
								System.out.println(toHexString(p.getData()));
							listeners.stream().filter(ws4 -> ws4.getAddress().equals(p.getAddress().getHostAddress()))
							.forEach(ws5 -> ws5.setCurrentRequest(request2));
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (SocketException e1) {
				e1.printStackTrace();
			}

		});
		listenThread.start();
		WsSocket.sendDiscovery();
	}

	public void sendOn(WsSocket socket)
	{
		Callable<Boolean> send = socket.sendOn();
		sendCallable(Arrays.asList(send, socket.getSubscribe(), send, send));
	}

	public void sendOff(WsSocket socket)
	{
		Callable<Boolean> send = socket.sendOff();
		socket.sendSubscribe();
		sendCallable(Arrays.asList(send, socket.getSubscribe(), send, send));

	}

	private void sendCallable(List<Callable<Boolean>> send)
	{
		int reties = 3;
		Iterator<Callable<Boolean>> it = send.iterator();
		Future<Boolean> f = executor.submit(it.next());
		try
		{
			while (--reties > 0 && !f.get())
				f = executor.submit(it.next());
		} catch (InterruptedException | ExecutionException e)
		{
			e.printStackTrace();
		}
	}

	public void sendToggle(WsSocket socket)
	{
		Callable<Boolean> send = socket.sendToggle();
		sendCallable(Arrays.asList(send, socket.getSubscribe(), send, send));

	}

	public List<WsSocket> getListeners()
	{
		return listeners;
	}

	public void setListeners(List<WsSocket> listeners)
	{
		this.listeners = listeners;
	}

	public static String toHexString(byte[] data)
	{
		StringBuffer buffer = new StringBuffer();
		for (byte b : data)
			buffer.append(String.format("0x%02X,", Byte.toUnsignedInt(b)));
		return buffer.toString();
	}
	public void rescan()
	{
		WsSocket.sendDiscovery();
	}
}
