package dev.tuffsmp.shrinecraft.shrine;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The Shrine block: the only way to obtain the 14 items.
 * All behaviour of right-clicking lives in ShrineInteraction; this class just forwards to it.
 */
public class ShrineBlock extends BlockWithEntity {
    public static final MapCodec<ShrineBlock> CODEC = createCodec(ShrineBlock::new);
    /** Mirror of the block entity's stage, so the block can change model, light and particles without asking the block entity. */
    public static final EnumProperty<ShrineState> STATE = EnumProperty.of("state", ShrineState.class);

    // Outline = the three main parts of the altar model (base, pillar, basin).
    private static final VoxelShape SHAPE = VoxelShapes.union(
        Block.createCuboidShape(0, 0, 0, 16, 4, 16),
        Block.createCuboidShape(5, 4, 5, 11, 12, 11),
        Block.createCuboidShape(2, 12, 2, 14, 16, 14)
    );

    public ShrineBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(STATE, ShrineState.UNCONFIGURED));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ShrineBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /**
     * Right-click on the Shrine. NOTE: this is the 1.21.11 method name/signature (there is no "onUseWithoutItem" any more).
     * Sneak-right-click while holding items never reaches here in vanilla, so ShrineInteraction also listens to UseBlockCallback.
     */
    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return ShrineInteraction.handleUse(world, pos, player);
    }

    /** Distinct ambient particles per stage (client side). */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        switch (state.get(STATE)) {
            case UNCONFIGURED -> {
                // Swirling purple portal motes - "waiting for an admin".
                for (int i = 0; i < 3; i++) {
                    world.addParticleClient(ParticleTypes.PORTAL, x + (random.nextDouble() - 0.5) * 0.9, y + random.nextDouble() * 0.8, z + (random.nextDouble() - 0.5) * 0.9,
                        (random.nextDouble() - 0.5) * 0.6, -0.4 - random.nextDouble() * 0.4, (random.nextDouble() - 0.5) * 0.6);
                }
                if (random.nextInt(6) == 0) {
                    world.addParticleClient(ParticleTypes.END_ROD, x + (random.nextDouble() - 0.5) * 0.6, y + 0.1, z + (random.nextDouble() - 0.5) * 0.6, 0.0, 0.02, 0.0);
                }
            }
            case CONFIGURED -> {
                // Enchanting glyphs drifting up toward the floating preview.
                world.addParticleClient(ParticleTypes.ENCHANT, x + (random.nextDouble() - 0.5) * 1.2, y + 0.4, z + (random.nextDouble() - 0.5) * 1.2, 0.0, 0.6, 0.0);
                if (random.nextInt(4) == 0) {
                    world.addParticleClient(ParticleTypes.GLOW, x + (random.nextDouble() - 0.5) * 0.5, y + 0.2 + random.nextDouble(), z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.01, 0.0);
                }
            }
            case CLAIMED -> {
                // Cold ash - "spent".
                if (random.nextInt(5) == 0) {
                    world.addParticleClient(ParticleTypes.ASH, x + (random.nextDouble() - 0.5) * 0.8, y + random.nextDouble() * 0.4, z + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.01, 0.0);
                }
            }
        }
    }
}
