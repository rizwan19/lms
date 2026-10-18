import { EditorContent, useEditor, type Editor } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'

/** WYSIWYG editor limited to the structures the server's HTML allowlist keeps. */
export function RichTextEditor({ id, label, value, onChange }: { id: string; label: string; value: string; onChange: (html: string) => void }) {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [2, 3, 4] },
        link: { openOnClick: false, protocols: ['http', 'https', 'mailto'] },
      }),
    ],
    content: value,
    editorProps: { attributes: { id, role: 'textbox', 'aria-multiline': 'true', 'aria-label': label, class: 'rich-text' } },
    onUpdate: ({ editor: current }) => onChange(current.getHTML()),
  })

  return (
    <div className="field">
      <span className="label">{label}</span>
      {editor && <Toolbar editor={editor} />}
      <EditorContent editor={editor} />
    </div>
  )
}

function Toolbar({ editor }: { editor: Editor }) {
  const button = (name: string, action: () => void, active: boolean) => (
    <button type="button" aria-pressed={active} onClick={action}>
      {name}
    </button>
  )
  return (
    <div className="toolbar" role="toolbar" aria-label="Formatting">
      {button('Bold', () => editor.chain().focus().toggleBold().run(), editor.isActive('bold'))}
      {button('Italic', () => editor.chain().focus().toggleItalic().run(), editor.isActive('italic'))}
      {button('Heading', () => editor.chain().focus().toggleHeading({ level: 2 }).run(), editor.isActive('heading', { level: 2 }))}
      {button('Bullets', () => editor.chain().focus().toggleBulletList().run(), editor.isActive('bulletList'))}
      {button('Numbered', () => editor.chain().focus().toggleOrderedList().run(), editor.isActive('orderedList'))}
      {button(
        'Link',
        () => {
          const href = window.prompt('Link address (https://…)')
          if (href) editor.chain().focus().extendMarkRange('link').setLink({ href }).run()
          else editor.chain().focus().unsetLink().run()
        },
        editor.isActive('link'),
      )}
    </div>
  )
}
