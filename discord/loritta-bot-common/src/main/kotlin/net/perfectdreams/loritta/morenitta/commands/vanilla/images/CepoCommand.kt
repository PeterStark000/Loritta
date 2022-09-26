package net.perfectdreams.loritta.morenitta.commands.vanilla.images

import net.perfectdreams.loritta.morenitta.commands.AbstractCommand
import net.perfectdreams.loritta.morenitta.commands.CommandContext
import net.perfectdreams.loritta.morenitta.gifs.CepoDeMadeiraGIF
import net.perfectdreams.loritta.morenitta.utils.Constants
import net.perfectdreams.loritta.morenitta.utils.MiscUtils
import net.perfectdreams.loritta.morenitta.api.commands.Command
import net.perfectdreams.loritta.common.locale.BaseLocale
import net.perfectdreams.loritta.common.locale.LocaleKeyData
import net.perfectdreams.loritta.morenitta.utils.OutdatedCommandUtils

class CepoCommand : AbstractCommand("cepo", category = net.perfectdreams.loritta.common.commands.CommandCategory.IMAGES) {
	override fun getDescriptionKey() = LocaleKeyData("commands.command.cepo.description")
	override fun getExamplesKey() = Command.SINGLE_IMAGE_EXAMPLES_KEY
	// TODO: Fix Usage

	override fun needsToUploadFiles() = true

	override suspend fun run(context: CommandContext,locale: BaseLocale) {
		OutdatedCommandUtils.sendOutdatedCommandMessage(context, locale, "brmemes cepo")

		val contextImage = context.getImageAt(0) ?: run { Constants.INVALID_IMAGE_REPLY.invoke(context); return; }
		val file = CepoDeMadeiraGIF.getGIF(contextImage)

		MiscUtils.optimizeGIF(file)
		context.sendFile(file, "cepo.gif", context.getAsMention(true))
		file.delete()
	}
}