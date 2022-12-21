/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Observable;
import java.util.LinkedList;
import java.util.StringTokenizer;

/**
 This object is executed as a separate thread so it implements Runnable
 * and since its part of Observer design pattern it extends Observable.
 * Once a message has been received(it blocks on readObject())it notifies the parent which is 
 * CTCPListener
 */
public class CTCPLinkServer extends Observable implements Runnable{
    private Socket        m_socket;
    private int           m_nLinkId;

    public CTCPLinkServer(int x_nLinkId,Socket x_sock) 
    {
        m_nLinkId = x_nLinkId;
        m_socket = x_sock;
    }
    
    public void run()
    {
        ObjectInputStream objInput = null;
        //BufferedReader dataInput = null;
        try
        {
            objInput = new ObjectInputStream(m_socket.getInputStream());
            //dataInput = new BufferedReader(new InputStreamReader( m_socket.getInputStream()));
        }
        catch(IOException ex)
        {
            ex.printStackTrace();
            System.exit(0);
        }
        
        while(true)
        {
            try
            {
                CInterProcessMessage msg = (CInterProcessMessage)objInput.readObject();
                if(msg != null)
                {
                    setChanged();
                    notifyObservers(msg);
                }
                else
                {
                    return;
                }
            }
            catch(IOException ex)
            {
                //System.out.println("Exception in TCP Link Server...");
                return;
            } 
            catch(Exception ex)
            {
                ex.printStackTrace();
                return;
            }
        }
    }   
}
