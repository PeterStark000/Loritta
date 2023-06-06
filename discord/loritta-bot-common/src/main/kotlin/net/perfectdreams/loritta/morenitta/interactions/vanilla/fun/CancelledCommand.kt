package net.perfectdreams.loritta.morenitta.interactions.vanilla.`fun`

import net.perfectdreams.i18nhelper.core.keydata.StringI18nData
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.mentionUser
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.options.LocalizedApplicationCommandOptions
import net.perfectdreams.loritta.cinnamon.discord.interactions.commands.styled
import net.perfectdreams.loritta.cinnamon.emotes.Emotes
import net.perfectdreams.loritta.common.commands.CommandCategory
import net.perfectdreams.loritta.i18n.I18nKeysData
import net.perfectdreams.loritta.morenitta.interactions.UnleashedContext
import net.perfectdreams.loritta.morenitta.interactions.commands.*
import net.perfectdreams.loritta.morenitta.interactions.commands.options.ApplicationCommandOptions
import net.perfectdreams.loritta.morenitta.interactions.commands.options.OptionReference

class CancelledCommand : SlashCommandDeclarationWrapper  {
    companion object {
        val I18N_PREFIX = I18nKeysData.Commands.Command.Cancelled
    }

    override fun command() = slashCommand(I18N_PREFIX.Label, I18N_PREFIX.Description, CommandCategory.FUN) {
        enableLegacyMessageSupport = true

        examples = I18N_PREFIX.Examples

        this.alternativeLegacyLabels.apply {
            add("cancelado")
            add("cancel")
        }

        executor = CancelledExecutor()
    }

    inner class CancelledExecutor : LorittaSlashCommandExecutor(), LorittaLegacyMessageCommandExecutor {
        inner class Options : ApplicationCommandOptions() {
            val user = user("user", I18N_PREFIX.Options.User)
        }

        override val options = Options()

        override suspend fun execute(context: UnleashedContext, args: SlashCommandArguments) {
            val (user, _) = args[options.user]

            context.reply(false) {
                styled(
                    content = context.i18nContext.get(
                        I18N_PREFIX.WasCancelled(
                            user.asMention,
                            context.i18nContext.get(I18N_PREFIX.Reasons).random()
                        )
                    ),
                    prefix = Emotes.LoriHmpf.toString()
                )
            }
        }

        override suspend fun convertToInteractionsArguments(
            context: LegacyMessageCommandContext,
            args: List<String>
        ): Map<OptionReference<*>, Any?>? {
            val userAndMember = context.getUserAndMember(0)
            if (userAndMember == null) {
                context.explain()
                return null
            }

            return mapOf(options.user to userAndMember)
        }
    }
}