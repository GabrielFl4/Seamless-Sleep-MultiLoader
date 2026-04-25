package net.aqualoco.sec.mixin.sleep;

import net.aqualoco.sec.acceleration.WorldSleepAccelerationManager;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationModuleStatus;
import net.aqualoco.sec.acceleration.WorldSleepAccelerationStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FuelValues;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityAccelerationMixin {
    @Shadow
    @Final
    protected NonNullList<ItemStack> items;

    @Shadow
    int litTimeRemaining;

    @Shadow
    int litTotalTime;

    @Shadow
    int cookingTimer;

    @Shadow
    int cookingTotalTime;

    @Shadow
    @Final
    private RecipeManager.CachedCheck<SingleRecipeInput, ? extends AbstractCookingRecipe> quickCheck;

    @Shadow
    protected abstract int getBurnDuration(FuelValues fuelValues, ItemStack itemStack);

    @Shadow
    public abstract void setRecipeUsed(RecipeHolder<?> recipeHolder);

    @Shadow
    private static boolean canBurn(net.minecraft.core.RegistryAccess registryAccess,
                                   RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
                                   SingleRecipeInput recipeInput,
                                   NonNullList<ItemStack> items,
                                   int maxStackSize) {
        throw new AssertionError();
    }

    @Shadow
    private static boolean burn(net.minecraft.core.RegistryAccess registryAccess,
                                RecipeHolder<? extends AbstractCookingRecipe> recipeHolder,
                                SingleRecipeInput recipeInput,
                                NonNullList<ItemStack> items,
                                int maxStackSize) {
        throw new AssertionError();
    }

    @Shadow
    private static int getTotalCookTime(ServerLevel level, AbstractFurnaceBlockEntity blockEntity) {
        throw new AssertionError();
    }

    @Unique
    private boolean seamlesssleep$cycleLocked;

    @Unique
    private double seamlesssleep$lockedCycleMultiplier = 1.0D;

    @Unique
    private double seamlesssleep$productiveBurnCarry;

    @Unique
    private double seamlesssleep$idleBurnCarry;

    @Unique
    private boolean seamlesssleep$fuelChangedThisTick;

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void seamlesssleep$accelerateProcessTick(ServerLevel level,
                                                            BlockPos pos,
                                                            BlockState state,
                                                            AbstractFurnaceBlockEntity blockEntity,
                                                            CallbackInfo ci) {
        AbstractFurnaceBlockEntityAccelerationMixin mixin = (AbstractFurnaceBlockEntityAccelerationMixin) (Object) blockEntity;
        WorldSleepAccelerationStatus status = WorldSleepAccelerationManager.getStatus(level);
        WorldSleepAccelerationModuleStatus processStatus = status.getProcess();
        boolean accelerationActive = status.isActive()
                && processStatus.isActive()
                && processStatus.coversChunk(ChunkPos.asLong(pos))
                && processStatus.getEffectiveTickMultiplier() > 1.0D;
        boolean continueLockedCycle = mixin.seamlesssleep$cycleLocked && mixin.cookingTimer > 0;

        if (!accelerationActive && !continueLockedCycle) {
            mixin.seamlesssleep$resetIdleBurnCarry();
            if (mixin.cookingTimer <= 0) {
                mixin.seamlesssleep$unlockCycle();
            }
            return;
        }

        ci.cancel();
        mixin.seamlesssleep$runAcceleratedTick(
                level,
                pos,
                state,
                accelerationActive ? processStatus.getEffectiveTickMultiplier() : 1.0D
        );
    }

    @Unique
    private void seamlesssleep$runAcceleratedTick(ServerLevel level,
                                                  BlockPos pos,
                                                  BlockState state,
                                                  double requestedMultiplier) {
        boolean wasLit = this.seamlesssleep$isLit();
        boolean changed = false;
        this.seamlesssleep$fuelChangedThisTick = false;

        if (!wasLit) {
            seamlesssleep$resetIdleBurnCarry();
        }

        if (this.cookingTimer <= 0 && this.seamlesssleep$cycleLocked) {
            seamlesssleep$unlockCycle();
            changed |= seamlesssleep$restoreUnlockedCookTime(level);
        }

        ItemStack inputStack = this.items.get(0);
        boolean hasInput = !inputStack.isEmpty();
        SingleRecipeInput input = new SingleRecipeInput(inputStack);
        RecipeHolder<? extends AbstractCookingRecipe> recipe = hasInput
                ? this.quickCheck.getRecipeFor(input, level).orElse(null)
                : null;
        int maxStackSize = ((AbstractFurnaceBlockEntity) (Object) this).getMaxStackSize();
        boolean canBurnNow = canBurn(level.registryAccess(), recipe, input, this.items, maxStackSize);

        if (requestedMultiplier > 1.0D && canBurnNow && !this.seamlesssleep$cycleLocked) {
            changed |= seamlesssleep$lockCurrentCycle(level, requestedMultiplier);
        }

        boolean productiveAcceleration = this.seamlesssleep$cycleLocked && canBurnNow;
        boolean hasFuel = !this.items.get(1).isEmpty();

        if (this.seamlesssleep$isLit() || hasFuel && hasInput) {
            if (productiveAcceleration) {
                seamlesssleep$resetIdleBurnCarry();
                if (seamlesssleep$consumeProductiveBurn(level, hasInput, canBurnNow)) {
                    this.cookingTimer++;
                    if (this.cookingTimer >= this.cookingTotalTime) {
                        this.cookingTimer = 0;
                        this.cookingTotalTime = getTotalCookTime(level, (AbstractFurnaceBlockEntity) (Object) this);
                        if (burn(level.registryAccess(), recipe, input, this.items, maxStackSize)) {
                            this.setRecipeUsed(recipe);
                        }
                        changed = true;
                        seamlesssleep$unlockCycle();
                    }
                }
            } else {
                if (this.seamlesssleep$isLit()) {
                    this.litTimeRemaining--;
                    this.seamlesssleep$applyIdleBurnAcceleration(requestedMultiplier, canBurnNow);
                }

                inputStack = this.items.get(0);
                hasInput = !inputStack.isEmpty();
                input = new SingleRecipeInput(inputStack);
                recipe = hasInput ? this.quickCheck.getRecipeFor(input, level).orElse(null) : null;
                canBurnNow = canBurn(level.registryAccess(), recipe, input, this.items, maxStackSize);

                if ((this.seamlesssleep$isLit() || !this.items.get(1).isEmpty() && hasInput) && !this.seamlesssleep$isLit() && canBurnNow) {
                    changed |= seamlesssleep$tryConsumeFuel(level, hasInput, canBurnNow);
                }

                if (this.seamlesssleep$isLit() && canBurnNow) {
                    if (this.cookingTotalTime <= 0) {
                        this.cookingTotalTime = getTotalCookTime(level, (AbstractFurnaceBlockEntity) (Object) this);
                    }

                    this.cookingTimer++;
                    if (this.cookingTimer >= this.cookingTotalTime) {
                        this.cookingTimer = 0;
                        this.cookingTotalTime = getTotalCookTime(level, (AbstractFurnaceBlockEntity) (Object) this);
                        if (burn(level.registryAccess(), recipe, input, this.items, maxStackSize)) {
                            this.setRecipeUsed(recipe);
                        }
                        changed = true;
                    }
                } else if (this.cookingTimer != 0) {
                    this.cookingTimer = 0;
                    if (this.seamlesssleep$cycleLocked) {
                        seamlesssleep$unlockCycle();
                        changed |= seamlesssleep$restoreUnlockedCookTime(level);
                    }
                }
            }
        } else if (!this.seamlesssleep$isLit() && this.cookingTimer > 0) {
            seamlesssleep$resetIdleBurnCarry();
            this.cookingTimer = Mth.clamp(this.cookingTimer - 2, 0, this.cookingTotalTime);
            if (this.cookingTimer <= 0 && this.seamlesssleep$cycleLocked) {
                seamlesssleep$unlockCycle();
                changed |= seamlesssleep$restoreUnlockedCookTime(level);
            }
        }

        if (!this.seamlesssleep$isLit()) {
            seamlesssleep$resetIdleBurnCarry();
        }

        boolean litNow = this.seamlesssleep$isLit();
        if (wasLit != litNow) {
            changed = true;
            state = state.setValue(AbstractFurnaceBlock.LIT, litNow);
            level.setBlock(pos, state, 3);
        }

        if (this.cookingTimer <= 0 && this.seamlesssleep$cycleLocked) {
            seamlesssleep$unlockCycle();
            changed |= seamlesssleep$restoreUnlockedCookTime(level);
        }

        if (changed || this.seamlesssleep$fuelChangedThisTick) {
            BlockEntitySetChangedInvoker.seamlesssleep$invokeSetChanged(level, pos, state);
        }
    }

    @Unique
    private boolean seamlesssleep$lockCurrentCycle(ServerLevel level, double multiplier) {
        int baseCookTime = this.cookingTotalTime > 0
                ? this.cookingTotalTime
                : getTotalCookTime(level, (AbstractFurnaceBlockEntity) (Object) this);
        int effectiveCookTime = seamlesssleep$computeEffectiveCookTime(baseCookTime, multiplier);
        int remappedProgress = seamlesssleep$remapProgress(
                this.cookingTimer,
                baseCookTime,
                effectiveCookTime,
                multiplier
        );

        boolean changed = !this.seamlesssleep$cycleLocked
                || Math.abs(this.seamlesssleep$lockedCycleMultiplier - multiplier) > 0.0001D
                || this.cookingTotalTime != effectiveCookTime
                || this.cookingTimer != remappedProgress;

        this.seamlesssleep$cycleLocked = true;
        this.seamlesssleep$lockedCycleMultiplier = Math.max(1.0D, multiplier);
        this.seamlesssleep$productiveBurnCarry = 0.0D;
        this.cookingTotalTime = effectiveCookTime;
        this.cookingTimer = Mth.clamp(remappedProgress, 0, Math.max(0, effectiveCookTime - 1));
        return changed;
    }

    @Unique
    private boolean seamlesssleep$consumeProductiveBurn(ServerLevel level, boolean hasInput, boolean canBurnNow) {
        int burnUnits = seamlesssleep$takeProductiveBurnUnits();
        while (burnUnits > 0) {
            if (this.litTimeRemaining <= 0 && !seamlesssleep$tryConsumeFuel(level, hasInput, canBurnNow)) {
                return false;
            }

            int consumed = Math.min(this.litTimeRemaining, burnUnits);
            this.litTimeRemaining -= consumed;
            burnUnits -= consumed;
        }
        return true;
    }

    @Unique
    private boolean seamlesssleep$tryConsumeFuel(ServerLevel level,
                                                 boolean hasInput,
                                                 boolean canBurnNow) {
        ItemStack fuelStack = this.items.get(1);
        if (!hasInput || !canBurnNow || fuelStack.isEmpty()) {
            return false;
        }

        int burnDuration = this.getBurnDuration(level.fuelValues(), fuelStack);
        if (burnDuration <= 0) {
            return false;
        }

        this.litTimeRemaining = burnDuration;
        this.litTotalTime = burnDuration;

        ItemStack remainder = fuelStack.getItem().getCraftingRemainder();
        if (!remainder.isEmpty()) {
            this.items.set(1, remainder);
        } else {
            fuelStack.shrink(1);
            if (fuelStack.isEmpty()) {
                this.items.set(1, remainder);
            }
        }

        this.seamlesssleep$fuelChangedThisTick = true;
        return true;
    }

    @Unique
    private boolean seamlesssleep$restoreUnlockedCookTime(ServerLevel level) {
        int restoredCookTime = getTotalCookTime(level, (AbstractFurnaceBlockEntity) (Object) this);
        if (this.cookingTotalTime == restoredCookTime) {
            return false;
        }
        this.cookingTotalTime = restoredCookTime;
        return true;
    }

    @Unique
    private int seamlesssleep$takeProductiveBurnUnits() {
        double total = this.seamlesssleep$productiveBurnCarry + Math.max(1.0D, this.seamlesssleep$lockedCycleMultiplier);
        int burnUnits = Math.max(1, (int) Math.floor(total));
        this.seamlesssleep$productiveBurnCarry = total - burnUnits;
        return burnUnits;
    }

    @Unique
    private void seamlesssleep$applyIdleBurnAcceleration(double currentMultiplier, boolean canBurnNow) {
        if (canBurnNow || currentMultiplier <= 1.0D || !this.seamlesssleep$isLit()) {
            seamlesssleep$resetIdleBurnCarry();
            return;
        }

        double totalExtraBurn = this.seamlesssleep$idleBurnCarry + Math.max(0.0D, currentMultiplier - 1.0D);
        int extraBurnUnits = Math.max(0, (int) Math.floor(totalExtraBurn));
        this.seamlesssleep$idleBurnCarry = totalExtraBurn - extraBurnUnits;
        if (extraBurnUnits <= 0) {
            return;
        }

        this.litTimeRemaining = Math.max(0, this.litTimeRemaining - extraBurnUnits);
    }

    @Unique
    private void seamlesssleep$resetIdleBurnCarry() {
        this.seamlesssleep$idleBurnCarry = 0.0D;
    }

    @Unique
    private boolean seamlesssleep$isLit() {
        return this.litTimeRemaining > 0;
    }

    @Unique
    private int seamlesssleep$computeEffectiveCookTime(int baseCookTime, double multiplier) {
        return Math.max(1, (int) Math.round(baseCookTime / Math.max(1.0D, multiplier)));
    }

    @Unique
    private int seamlesssleep$remapProgress(int currentProgress,
                                            int currentTotalTime,
                                            int effectiveTotalTime,
                                            double multiplier) {
        int clampedTotal = Math.max(1, currentTotalTime);
        int clampedProgress = Mth.clamp(currentProgress, 0, clampedTotal);
        int remainingOld = Math.max(0, clampedTotal - clampedProgress);
        int remainingNew = remainingOld <= 0
                ? 0
                : (int) Math.ceil(remainingOld / Math.max(1.0D, multiplier));
        return Math.max(0, effectiveTotalTime - remainingNew);
    }

    @Unique
    private void seamlesssleep$unlockCycle() {
        this.seamlesssleep$cycleLocked = false;
        this.seamlesssleep$lockedCycleMultiplier = 1.0D;
        this.seamlesssleep$productiveBurnCarry = 0.0D;
    }
}
