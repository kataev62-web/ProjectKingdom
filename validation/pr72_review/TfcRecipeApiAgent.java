import java.lang.instrument.Instrumentation;

/** Diagnostic only: inspect the actual Forge-loaded TFC class, without modifying it. */
public final class TfcRecipeApiAgent {
    public static void premain(String args, Instrumentation instrumentation) {
        Thread thread=new Thread(()->{
            for(int attempt=0;attempt<120;attempt++){
                for(Class<?> type:instrumentation.getAllLoadedClasses()){
                    if(!type.getName().equals("net.dries007.tfc.common.recipes.AnvilRecipe"))continue;
                    System.out.println("REVIEW73 loader="+type.getClassLoader());
                    for(String method:new String[]{"getId","m_6423_"}){
                        try {System.out.println("REVIEW73 "+method+"="+type.getMethod(method));}
                        catch(NoSuchMethodException missing){System.out.println("REVIEW73 "+method+"=MISSING");}
                    }
                    return;
                }
                try {Thread.sleep(1000);}catch(InterruptedException ex){return;}
            }
            System.out.println("REVIEW73 INCONCLUSIVE: TFC recipe class was not loaded");
        },"TFC-API-review");
        thread.setDaemon(true);thread.start();
    }
}
