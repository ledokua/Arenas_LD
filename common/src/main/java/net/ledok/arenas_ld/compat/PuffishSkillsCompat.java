package net.ledok.arenas_ld.compat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.puffish.skillsmod.api.Category;
import net.puffish.skillsmod.api.Experience;
import net.puffish.skillsmod.api.SkillsAPI;

import java.util.Optional;

public class PuffishSkillsCompat {

    /**
     * Gets a player's total skill points from the configured skill tree.
     * @param player The player to check.
     * @return The player's total skill points, or 0 if the tree is not found.
     */
    public static int getPlayerLevel(Player player) {
        if(!(player instanceof ServerPlayer serverPlayer)) {
            return 0;
        }
        // Use the skill tree ID from the config file.
        Optional<Category> skillTree = SkillsAPI.getCategory(ResourceLocation.tryParse(net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().puffish_skills_tree_id));
        return skillTree.map(tree -> tree.getPointsTotal(serverPlayer)).orElse(0);
    }

    /**
     * The branch of a skill tree with the most unlocked nodes: node ids start with it ("rogue.t3.s2.b" → "rogue").
     * Empty when the tree is unknown or nothing is unlocked.
     */
    public static String topBranch(ServerPlayer player, ResourceLocation categoryId) {
        Optional<Category> tree = SkillsAPI.getCategory(categoryId);
        if (tree.isEmpty()) {
            return "";
        }
        java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
        tree.get().streamUnlockedSkills(player).forEach(skill -> {
            String id = skill.getId();
            int dot = id.indexOf('.');
            counts.merge(dot > 0 ? id.substring(0, dot) : id, 1, Integer::sum);
        });
        String best = "";
        int most = 0;
        for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > most) {
                best = entry.getKey();
                most = entry.getValue();
            }
        }
        return best;
    }

    /** Every unlocked node of every skill tree, "<category>/<node>", sorted. */
    public static java.util.List<String> unlockedSkills(ServerPlayer player) {
        java.util.List<String> out = new java.util.ArrayList<>();
        SkillsAPI.streamCategories().forEach(category ->
            category.streamUnlockedSkills(player).forEach(skill -> out.add(category.getId() + "/" + skill.getId())));
        out.sort(null);
        return out;
    }

    /**
     * Adds experience to the configured skill tree for a specific player.
     * @param player The player to grant experience to.
     * @param amount The amount of experience to add.
     */
    public static void addExperience(ServerPlayer player, int amount) {
        // Find the skill tree using the ID from your config file.
        Optional<Category> skillTree = SkillsAPI.getCategory(ResourceLocation.tryParse(net.ledok.arenas_ld.config.ArenasLdConfig.getInstance().puffish_skills_tree_id));

        // If the category exists, get its experience handler and add the experience.
        skillTree.ifPresent(category -> {
            Optional<Experience> experienceHandler = category.getExperience();
            experienceHandler.ifPresent(exp -> exp.addTotal(player, amount));
        });
    }
}
