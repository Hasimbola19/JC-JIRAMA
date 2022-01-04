package sid2;

import java.rmi.Remote;
import java.rmi.RemoteException;

import javacard.framework.UserException;

public interface IJirama extends Remote{
	public void setCompteur(byte[] bytes) throws RemoteException,UserException;
	public byte[] getCompteur() throws RemoteException,UserException;
	public byte[] incrementer(byte [] Arr1, byte [] Arr2) throws RemoteException,UserException;
	public byte[] decrementer(byte [] Arr1, byte [] Arr2) throws RemoteException,UserException;
	public void setNumber(byte[] bytes) throws RemoteException,UserException;
	public byte[] getNumber() throws RemoteException,UserException;
}