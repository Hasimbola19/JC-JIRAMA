package sid2;

import java.rmi.RemoteException;

import javacard.framework.APDU;
import javacard.framework.UserException;
import javacard.framework.Util;
import javacard.framework.service.CardRemoteObject;
import  javacard.framework.*;

public class JiramaImpl extends CardRemoteObject implements IJirama{

	private byte[] compteur = {};
	private byte[] number = {0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x01};

	public void setCompteur(byte[] bytes) throws RemoteException, UserException {
		// TODO Auto-generated method stub
		this.compteur = bytes;
	}

	public byte[] getCompteur() throws RemoteException, UserException {
		// TODO Auto-generated method stub
		return compteur;
	}

	public byte[] incrementer(byte[] compteur,byte[] number) throws RemoteException, UserException {
		// TODO Auto-generated method stub
		 short i;
	        short [] X = new short [(short) 6];
	        short len = (short) compteur.length ;
	        short Alen = (short) (len - 1);
	        byte [] AddArr = new byte [len];
	        short R = (short) 0;
	        byte[] Z = new byte [(short) 2];

	        for (i = Alen; i >= 0 ;  i--)
	        {                       
	            X[i] =   (short) ((compteur[i] & 0xFF) + (number[i]&0xff) + R);
	            Util.setShort(Z, (short) 0, (short) X[i]);
	            AddArr[i] = (byte) Z[1];
	            R = (short) (Z[0]);
	        }
	        return AddArr;
	}

	public byte[] decrementer(byte[] compteur,byte[] number) throws RemoteException, UserException {
		 short i;
	        short [] X = new short [(short) 6];
	        short len = (short) compteur.length ;
	        short Alen = (short) (len - 1);
	        byte [] AddArr = new byte [len];
	        short R = (short) 0;
	        byte[] Z = new byte [(short) 2];

	        for (i = Alen; i >= 0 ;  i--)
	        {                       
	            X[i] =   (short) ((compteur[i] & 0xFF) - (number[i]&0xff) + R);
	            Util.setShort(Z, (short) 0, (short) X[i]);
	            AddArr[i] = (byte) Z[1];
	            R = (short) (Z[0]);
	        }
	        return AddArr;
	}

	public byte[] getNumber()  throws RemoteException, UserException {
		return number;
	}

	public void setNumber(byte[] number)  throws RemoteException, UserException {
		this.number = number;
	}
}
