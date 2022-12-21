
/*
I used this small utility class to measure the actual time passing after sleeps.
It returns the past time between start() and Stop call 
*/

public class CStopwatch {
    
    private long start;
    public boolean m_bstarted;
    public CStopwatch() 
    {
        m_bstarted = false;        
    } 
    public void Start()
    {
        start = System.currentTimeMillis();
        m_bstarted = true;
    }
    public long elapsedTime() 
    {
        long now = System.currentTimeMillis();
        return (now - start);
    }
    public void Stop()
    {
        m_bstarted = false;
    }
    public boolean IsStarted()
    {
        return m_bstarted;
    }
    
}
