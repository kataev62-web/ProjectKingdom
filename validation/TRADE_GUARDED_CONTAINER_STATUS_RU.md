# Managed Container boundary — 2026-10-16

Добавлен `TradeGuardedContainer` как обёртка над `net.minecraft.world.Container`.
Обёртка проверяет владельца, отклоняет изменения во время торговой блокировки,
возвращает копию ItemStack при чтении, контролирует setItem/removeItem/clearContent,
а также canPlaceItem/canTakeItem для стандартных меню и воронок, использующих обёртку.

Ограничения: существующие блоки и capability Forge пока не заменены этой обёрткой;
другие моды могут получить исходный Container/IItemHandler и обойти защиту.
Не проверена компиляция ForgeGradle. Запрещено включать как полную защиту торговли.
Следующий шаг: собственный BlockEntity + capability IItemHandler, выдающий только
контролируемый обработчик, затем проверка с реальными хопперами/Create/TFC.
