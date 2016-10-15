package com.swarly.wiwo_s20;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 
 * @author arik
 *
 */
public class WsSocket
{

	private static final int PORT = 10000;

	private final static byte[] twenties =
		{ 0x20, 0x20, 0x20, 0x20, 0x20, 0x20 };
	private final static byte[] zeros =
		{ 0x00, 0x00, 0x00, 0x00 };

	private static final byte[] MAGIC =
		{ 0x68, 0x64 };

	private static final byte[] SUBSCRIBE =
		{ 0x00, 0x1e, 0x63, 0x6c };
	private static final byte[] SUBSCRIBE_RES =
		{ 0x00, 0x18, 0x63, 0x6c };

	private static final byte[] CONTROL =
		{ 0x00, 0x17, 0x64, 0x63 };
	private static final byte[] CONTROL_RES =
		{ 0x00, 0x17, 0x73, 0x66 };

	private static final byte[] DISCOVERY =
		{ 0x00, 0x06, 0x71, 0x61 };
	private static final byte[] DISCOVERY_RES =
		{ 0x00, 0x2a, 0x71, 0x61 };

	private byte[] macAddress;

	private boolean powered;
	private String address;
	private boolean isAnswered;

	private Request currentRequest;
	private WsSocketListner socketListener;

	public WsSocketListner getSocketListener()
	{
		return socketListener;
	}

	public void setSocketListener(WsSocketListner socketListener)
	{
		this.socketListener = socketListener;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((address == null) ? 0 : address.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WsSocket other = (WsSocket) obj;
		if (address == null)
		{
			if (other.address != null)
				return false;
		} else if (!address.equals(other.address))
			return false;
		return true;
	}

	public boolean isPowered()
	{
		return powered;
	}

	public String getAddress()
	{
		return address;
	}

	public Request getCurrentRequest()
	{
		return currentRequest;
	}

	public void setCurrentRequest(Request currentRequest)
	{
		isAnswered = currentRequest == this.currentRequest;
		this.currentRequest = currentRequest;
	}

	public boolean isAnswered()
	{
		return isAnswered;
	}

	public void setAddress(String address)
	{
		this.address = address;
	}

	public void setPowered(boolean powered)
	{
		this.powered = powered;
	}

	public byte[] getMacAddress()
	{
		return macAddress;
	}

	private byte[] getReversedMacAddress()
	{
		byte[] reversed = new byte[macAddress.length];
		for (int i = 0; i < reversed.length; i++)
			reversed[i] = macAddress[macAddress.length - 1 - i];
		return reversed;
	}

	public void setMacAddress(byte[] macAddress)
	{
		this.macAddress = macAddress;
	}

	private void sendRequest(Request request)
	{
		sendRequest(request, false);
		isAnswered = false;
	}

	private void sendRequest(Request request, boolean isBroadCast)
	{
		byte[] data = request.getRequest(this);
		this.currentRequest = request;
		try (DatagramSocket d = new DatagramSocket();)
		{
			d.send(new DatagramPacket(data, data.length, InetAddress.getByName(
					isBroadCast ? "255.255.255.255" : address), PORT));
		} catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	Callable<Boolean> sendOn()
	{
		return () ->
		{
			if (powered)
				return true;
			powered = true;
			sendRequest(Request.Control);
			Thread.sleep(500);
			return isAnswered;
		};
	}

	Callable<Boolean> sendOff()
	{
		return () ->
		{
			if (!powered)
				return true;
			powered = false;
			sendRequest(Request.Control);
			Thread.sleep(500);
			return isAnswered;
		};

	}

	public void sendSubscribe()
	{
		sendRequest(Request.Subscribe);
	}

	public static void sendDiscovery()
	{
		new WsSocket().sendRequest(Request.Discovery, true);
	}

	public Callable<Boolean> sendToggle()
	{
		return () ->
		{
			powered = !powered;
			sendRequest(Request.Control);
			Thread.sleep(500);
			return isAnswered;
		};
	}

	public Callable<Boolean> getSubscribe()
	{
		return () ->
		{
			powered = !powered;
			sendRequest(Request.Subscribe);
			Thread.sleep(500);
			return isAnswered;
		};
	}

	public enum Request
	{
		Discovery((s) -> join(MAGIC, DISCOVERY),
				(d) -> new String(d).contains(new String(DISCOVERY_RES)),
				7), Control((s) -> join(MAGIC, CONTROL, s.getMacAddress(),
						twenties, zeros, new byte[]
								{ (byte) (s.isPowered() ? 0x1 : 0x0) }), (d) -> new String(d).contains(new String(CONTROL_RES)), 6), Subscribe((s) -> join(MAGIC, SUBSCRIBE, s.getMacAddress(), twenties, s.getReversedMacAddress(), twenties), (d) -> new String(d).contains(new String(SUBSCRIBE_RES)), 6), Unknown((s) -> join(MAGIC), (d) -> true, 0);

		private Function<WsSocket, byte[]> generateRequest;

		private Predicate<byte[]> checkIfResponse;

		private int startMacAddress;

		private Request(Function<WsSocket, byte[]> generateRequest,
				Predicate<byte[]> checkIfResponse, int startMacAddress)
		{
			this.generateRequest = generateRequest;
			this.checkIfResponse = checkIfResponse;
			this.startMacAddress = startMacAddress;
		}

		public Function<WsSocket, byte[]> getGenerateRequest()
		{
			return generateRequest;
		}

		public Predicate<byte[]> getCheckIfResponse()
		{
			return checkIfResponse;
		}

		public boolean isResponsePacket(byte[] data)
		{
			return checkIfResponse.test(data);
		}

		public byte[] getRequest(WsSocket socket)
		{

			return generateRequest.apply(socket);
		}

		public byte[] getMacAddress(byte[] packet)
		{
			return Arrays.copyOfRange(packet, this.startMacAddress,
					this.startMacAddress + 6);
		}
	}

	public static byte[] join(byte[]... arrays)
	{
		if (arrays.length == 1)
			return arrays[0];
		int sum = 0;
		for (byte[] a : arrays)
			sum += a.length;
		byte[] result = new byte[sum];
		int currentIndex = 0;
		for (byte[] a : arrays)
			for (byte b : a)
				result[currentIndex++] = b;
		return result;
	}

	public void update(byte[] data)
	{
		powered = data[22] != 0;
	}
}
