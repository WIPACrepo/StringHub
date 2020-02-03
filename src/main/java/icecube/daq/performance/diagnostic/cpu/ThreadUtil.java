package icecube.daq.performance.diagnostic.cpu;

import com.sun.tools.attach.VirtualMachine;
import org.apache.log4j.Logger;
import sun.tools.attach.HotSpotVirtualMachine;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides thread utilities not available from JVM.
 *
 * Note: Some functionality requires tools.jar in the runtime classpath which is not
 *       a standard dependency.
 */
public class ThreadUtil
{
    static Logger logger = Logger.getLogger(ThreadUtil.class.getName());


    /**
     * Attempt to put tools.jar (or classes.jar on some Mac systems) on the classpath
     */
    static {
        try {
            String javaHome = System.getProperty("java.home");
            String toolsJarURL = "file:" + javaHome + "/../lib/tools.jar";

            // Make addURL public
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);

            URLClassLoader sysloader = (URLClassLoader)ClassLoader.getSystemClassLoader();
            if (sysloader.getResourceAsStream("/com/sun/tools/attach/VirtualMachine.class") == null) {
                method.invoke(sysloader, (Object) new URL(toolsJarURL));
                Thread.currentThread().getContextClassLoader().loadClass("com.sun.tools.attach.VirtualMachine");
                Thread.currentThread().getContextClassLoader().loadClass("com.sun.tools.attach.AttachNotSupportedException");
            }

        } catch (Exception e) {
            logger.error("Java home points to " + System.getProperty("java.home") + " make sure it is not a JRE path");
            logger.error("Failed to add tools.jar to classpath", e);
        }
    }

    /**
     * Thread data derived from a low-level thread dump or jstack
     * format. The nid value is not otherwise available and is required
     * in order to map a particular thread to other system diagnostics
     * such as top or ps or renice.
     */
    public static class ThreadInfo {
        public final String name;
        public final long tid;
        public final long nid;

        private final String tostring;

        public ThreadInfo(final String name, final long tid, final long nid)
        {
            this.name = name;
            this.tid = tid;
            this.nid = nid;
            this.tostring = "name: [" + name + "]," +
                    " tid: [" + tid + "], nid: [" + nid + "]";
        }

        @Override
        public String toString()
        {
            return tostring;
        }
    }

    /**
     * Non-portable mechanism to learn the process id of the local VM.
     * @return The process id of the local VM, or -1 on failure.
     */
    public static int myPID()
    {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        try
        {
            return Integer.parseInt(name.split("@")[0]);
        }
        catch (Throwable th)
        {
            return -1;
        }
    }

    /**
     * Non-portable mechanism to learn the native id for each thread in
     * a VM.
     *
     * @return The Thread details for each thread in the VM.
     @throws Exception An error occurred, likely from the non-standard
      *         mechanism for obtaining native thread ids.
     */
    public static List<ThreadInfo> getThreadInfo() throws Exception
    {
        return getThreadInfo(myPID());
    }

    /**
     * Non-portable mechanism to learn the native id for each thread in
     * a VM.
     *
     * @param pid The process id of the VM.
     * @return The Thread details for exch thread in the VM.
     * @throws Exception An error occurred, likely from the non-standard
     *         mechanism for obtaining native thread ids.
     */
    public static List<ThreadInfo> getThreadInfo(int pid) throws Exception
    {
        List<ThreadInfo> ret = new ArrayList<>();

        VirtualMachine vm = null;
        BufferedReader reader = null;
        try
        {
            vm = VirtualMachine.attach(Integer.toString(pid));

            // NOTE: Non-Portable cast
            InputStream in = ((HotSpotVirtualMachine)vm).remoteDataDump();
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));

            // consume dump, parsing thread info from thread lines:
            //"main" #1 prio=5 os_prio=0 tid=0x00007fea2800c800 nid=0x62bf runnable [0x00007fea2e978000]
            Pattern pat = Pattern.compile("\"(.*)\".*tid=0x([0-9a-fA-F]+).*nid=0x([0-9a-fA-F]+).*");

            String line;
            while((line = reader.readLine()) != null)
            {
                Matcher matcher = pat.matcher(line);
                if (matcher.find()) {
                    String name =  matcher.group(1);
                    long tid = Long.parseLong(matcher.group(2), 16);
                    int nid =Integer.parseInt(matcher.group(3), 16);
                    ret.add(new ThreadInfo(name, tid, nid));
                }
            }

            return ret;
        }
        finally
        {
            if(reader != null)
            {
                reader.close();
            }
            if(vm != null)
            {
                vm.detach();
            }
        }

    }

    /**
     *
     * @param pid The pid of the jvm process
     * @throws Exception An error occurred, likely from the non-standard
     *         mechanism for obtaining native thread ids.
     */
    static void printThreadInfo(int pid) throws Exception
    {
        List<ThreadInfo> threadInfo = getThreadInfo(pid);
        for(ThreadInfo ti : threadInfo)
        {
            System.out.println(ti);
        }
    }

    public static void main(String[] args) throws Exception
    {

        System.out.println("JVM Thread listing...");
        Set<Thread> all = Thread.getAllStackTraces().keySet();
        for( Thread th : all)
        {
            System.out.println(th);
        }

        System.out.println();
        System.out.println();

        System.out.println("Hotspot Thread listing...");
        printThreadInfo(myPID());


    }

}
