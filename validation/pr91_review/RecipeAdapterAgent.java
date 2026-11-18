import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.util.Collection;

/** Read-only probe of a REAL registered TFC recipe through the production adapter. */
public final class RecipeAdapterAgent {
    public static void premain(String args,Instrumentation instrumentation){
        Thread t=new Thread(()->{
            String last="recipe manager not ready";
            for(int attempt=0;attempt<180;attempt++){
                try {
                    Class<?> recipeType=null,adapter=null;
                    for(Class<?> c:instrumentation.getAllLoadedClasses()){
                        if(c.getName().equals("net.dries007.tfc.common.recipes.AnvilRecipe"))recipeType=c;
                        if(c.getName().equals("ru.projectkingdom.npc.KingdomTfcSmithWork"))adapter=c;
                    }
                    if(recipeType!=null){
                        ClassLoader loader=recipeType.getClassLoader();
                        if(adapter==null)adapter=Class.forName("ru.projectkingdom.npc.KingdomTfcSmithWork",false,loader);
                        Object server=Class.forName("net.minecraftforge.server.ServerLifecycleHooks",false,loader)
                            .getMethod("getCurrentServer").invoke(null);
                        if(server!=null)for(Method getter:server.getClass().getMethods()){
                            if(getter.getParameterCount()!=0||!getter.getReturnType().getName().equals("net.minecraft.world.item.crafting.RecipeManager"))continue;
                            Object manager=getter.invoke(server);
                            for(Method rows:manager.getClass().getMethods()){
                                if(rows.getParameterCount()!=0||!Collection.class.isAssignableFrom(rows.getReturnType()))continue;
                                for(Object recipe:(Collection<?>)rows.invoke(manager))if(recipeType.isInstance(recipe)){
                                    String nativeId=recipeType.getMethod("m_6423_").invoke(recipe).toString();
                                    if(!nativeId.startsWith("tfc:"))continue;
                                    Method method=adapter.getDeclaredMethod("recipeId",Object.class);
                                    method.setAccessible(true);
                                    Object result=method.invoke(null,recipe);
                                    System.out.println("PROBE91 loader="+loader);
                                    System.out.println("PROBE91 native="+nativeId+" adapter="+result);
                                    System.out.println(nativeId.equals(result)?"PROBE91 PASS":"PROBE91 FAIL");
                                    return;
                                }
                            }
                        }
                    }
                }catch(Throwable ex){last=ex.toString();}
                try{Thread.sleep(1000);}catch(InterruptedException ex){return;}
            }
            System.out.println("PROBE91 INCONCLUSIVE: "+last);
        },"TFC-recipe-adapter-probe");
        t.setDaemon(true);t.start();
    }
}
